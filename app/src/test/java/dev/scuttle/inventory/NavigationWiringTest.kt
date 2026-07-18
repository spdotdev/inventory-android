package dev.scuttle.inventory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 7 shipped two Critical bugs that a full green suite + a self-review both
 * missed, because nothing asserted *reachability* — only that individual pieces
 * compiled and behaved correctly in isolation:
 *
 * 1. SearchScreen's route was declared but nothing navigated to it — Search had
 *    a bottom-nav tab removed and no replacement, so it went from reachable to
 *    orphaned with a fully green suite.
 * 2. The scanner route gained a second caller (the bottom-bar Scan tab) with a
 *    savedStateHandle delivery contract that only makes sense for its original
 *    caller (LocationDetailScreen) — scanning from the bar silently did nothing.
 *
 * Neither is expressible as a normal Compose/ViewModel unit test: this module's
 * JVM unit tests have no Robolectric or compose-ui-test on the classpath (only
 * the instrumented androidTest source set does — see the flow tests under
 * app/src/androidTest), so an actual NavHost can't be built or traversed here.
 * This file instead scans MainActivity.kt and the top-level screens' source as
 * plain text — the cheapest check that still discriminates: it fails if either
 * (a) MainActivity stops wiring a screen's search callback to Routes.search(...),
 * (b) a screen stops actually invoking that callback from the UI, or (c) the two
 * scanner callers' delivery branches stop being distinguishable from each other.
 */
class NavigationWiringTest {
    private val appRoot: File by lazy { findAppModuleRoot() }

    private fun read(relativePath: String): String {
        val file = File(appRoot, "src/main/java/dev/scuttle/inventory/$relativePath")
        check(file.exists()) { "Expected source file not found: ${file.absolutePath}" }
        return file.readText()
    }

    /** Walks up from the working directory to find the `app` module root, so this
     * test doesn't depend on Gradle's exact Test-task working-directory convention. */
    private fun findAppModuleRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/java/dev/scuttle/inventory/MainActivity.kt").exists()) return dir
            if (File(dir, "app/src/main/java/dev/scuttle/inventory/MainActivity.kt").exists()) {
                return File(dir, "app")
            }
            dir = dir.parentFile
        }
        error("Could not locate the app module's src/main from working dir ${File(".").absolutePath}")
    }

    /**
     * The text of the `composable(...)` block containing [marker] — from the
     * nearest preceding `composable(` up to (not including) the next one. Handles
     * both `composable(Routes.X) { ... }` and `composable(route = Routes.X, ...)`.
     */
    private fun composableBlock(
        source: String,
        marker: String,
    ): String {
        val markerIndex = source.indexOf(marker)
        check(markerIndex >= 0) { "Could not find `$marker` in MainActivity.kt — did a route/wiring rename?" }
        val blockStart = source.lastIndexOf("composable(", markerIndex)
        check(blockStart >= 0) { "`$marker` isn't inside a composable(...) block" }
        val nextBlock = source.indexOf("composable(", blockStart + "composable(".length)
        val blockEnd = if (nextBlock == -1) source.length else nextBlock
        return source.substring(blockStart, blockEnd)
    }

    // --- Critical 1: Search must be reachable from every top-level screen ---

    @Test
    fun `MainActivity wires Dashboard, Storage tab and Missing to Routes-search`() {
        val mainActivity = read("MainActivity.kt")
        listOf(
            "Dashboard" to "composable(Routes.DASHBOARD)",
            "Storage tab (Routes.HOME)" to "composable(Routes.HOME)",
            // GAP4-L8: MISSING_ITEMS's composable() call gained an `arguments = ...` line
            // (the `?fromDrawer=` back-arrow gate), so it's no longer `composable(Routes.
            // MISSING_ITEMS)` on one line — match just the route reference instead.
            "Missing items" to "composable(\n                Routes.MISSING_ITEMS,",
        ).forEach { (label, marker) ->
            val block = composableBlock(mainActivity, marker)
            assertTrue(
                "$label's composable() block never navigates via Routes.search(...) — Search would be " +
                    "unreachable from it, exactly Critical 1 from the Task 7 report.",
                block.contains("Routes.search("),
            )
        }
    }

    @Test
    fun `Dashboard, AllStoragesScreen and MissingItemsScreen each invoke onOpenSearch from the UI`() {
        listOf(
            "ui/dashboard/DashboardScreen.kt",
            "ui/home/AllStoragesScreen.kt",
            "ui/missing/MissingItemsScreen.kt",
        ).forEach { path ->
            val src = read(path)
            // Drop the parameter-declaration line ("onOpenSearch: (...) -> Unit = {}")
            // so a screen that merely accepts the callback without ever calling it
            // from a real UI element (button/click handler) still fails this check.
            val withoutDeclaration =
                src.lineSequence().filterNot { it.contains("onOpenSearch:") }.joinToString("\n")
            assertTrue(
                "$path declares onOpenSearch but never calls it from the UI — Search would be a dead " +
                    "parameter, not an actual entry point.",
                withoutDeclaration.contains("onOpenSearch"),
            )
        }
    }

    // --- Critical 2: the two scanner callers must stay distinguishable ---

    @Test
    fun `LocationDetailScreen opens the scanner in ADD mode, the bottom bar in LOOKUP mode`() {
        val mainActivity = read("MainActivity.kt")

        val locationBlock = composableBlock(mainActivity, "route = Routes.LOCATION,")
        assertTrue(
            "LocationDetailScreen's onOpenScanner no longer opens ScannerMode.ADD — its scanned code " +
                "would stop being deliverable back to the shelf it was opened from.",
            locationBlock.contains("Routes.scanner(ScannerMode.ADD)"),
        )

        // The bottom-bar Scan tab is defined via Icons.Filled.QrCodeScanner (its
        // unique marker in the tab list) rather than a composable(...) block — it's
        // a BottomTab entry, not a destination.
        val tabIndex = mainActivity.indexOf("Icons.Filled.QrCodeScanner")
        check(tabIndex >= 0) { "Could not find the bottom-bar Scan tab's icon in MainActivity.kt" }
        val tabWindow = mainActivity.substring(tabIndex, minOf(tabIndex + 300, mainActivity.length))
        assertTrue(
            "The bottom-bar Scan tab no longer navigates with ScannerMode.LOOKUP — scanning from the " +
                "bar would go back to inferring delivery from the back stack (Critical 2).",
            tabWindow.contains("ScannerMode.LOOKUP"),
        )
    }

    @Test
    fun `the SCANNER destination delegates to scanDeliveryActionFor and its two branches cannot be confused`() {
        val mainActivity = read("MainActivity.kt")
        val scannerBlock = composableBlock(mainActivity, "route = Routes.SCANNER,")

        assertTrue(
            "The SCANNER destination no longer calls scanDeliveryActionFor(...) — it may have gone " +
                "back to inferring the caller's intent from the back stack instead of the explicit mode " +
                "argument (which is what ScanDeliveryActionTest actually exercises).",
            scannerBlock.contains("scanDeliveryActionFor("),
        )

        // Split the block on the two sealed-subtype branches and check each only
        // does its OWN thing — this is the "cannot be confused with each other"
        // assertion the task calls for, not just "each individually looks right".
        val deliverIndex = scannerBlock.indexOf("DeliverToCaller ->")
        val navigateIndex = scannerBlock.indexOf("NavigateToSearch ->")
        check(deliverIndex >= 0 && navigateIndex >= 0) {
            "Expected both ScanDeliveryAction branches (DeliverToCaller, NavigateToSearch) in the " +
                "SCANNER destination's onScanned handler."
        }
        val (deliverBranch, navigateBranch) =
            if (deliverIndex < navigateIndex) {
                scannerBlock.substring(deliverIndex, navigateIndex) to scannerBlock.substring(navigateIndex)
            } else {
                scannerBlock.substring(deliverIndex) to scannerBlock.substring(navigateIndex, deliverIndex)
            }

        assertTrue(
            "DeliverToCaller branch must write to savedStateHandle (the add-to-shelf contract).",
            deliverBranch.contains("savedStateHandle"),
        )
        assertFalse(
            "DeliverToCaller branch must NOT also navigate to Search — that would mean a scan opened " +
                "from a shelf screen could ALSO silently redirect to Search, corrupting the add flow.",
            deliverBranch.contains("Routes.search("),
        )

        assertTrue(
            "NavigateToSearch branch must navigate via Routes.search(...) (the scan-to-lookup contract).",
            navigateBranch.contains("Routes.search("),
        )
        assertFalse(
            "NavigateToSearch branch must NOT also write scanned_code to the previous entry's " +
                "savedStateHandle — that's the ADD contract and there is no shelf screen underneath the " +
                "bottom-bar Scan tab to receive it.",
            navigateBranch.contains("savedStateHandle"),
        )
    }
}
