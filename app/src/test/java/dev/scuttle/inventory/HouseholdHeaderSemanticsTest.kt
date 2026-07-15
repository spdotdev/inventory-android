package dev.scuttle.inventory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the collapsible household header AllStoragesScreen.kt added
 * (Task 8): its clickable Row used to wrap `Modifier.clearAndSetSemantics { contentDescription
 * = toggleCollapsedCd }`, which DISCARDS every descendant's semantics and replaces them with
 * just that one contentDescription. The household's own name — `Text(text = entry.name, ...)`,
 * the only place that name is ever rendered on this screen — was a descendant of that Row, so
 * it silently dropped out of the semantics tree entirely:
 *  - a TalkBack user stopped hearing the household's name on this screen, hearing only the
 *    toggle's action description instead;
 *  - every `hasText("Home")` / `onNodeWithText("Home")` flow-test lookup on this screen started
 *    timing out — including tests this branch never touched (CreateLocationFlowTest,
 *    EmptyStorageFlowTest, SearchFlowTest, SearchNoResultsFlowTest), because they all navigate
 *    by tapping through the household name (final review, 2026-07-14).
 *
 * The fix announces the collapse/expand ACTION via `Modifier.clickable(onClickLabel = ...)`
 * instead of replacing the row's semantics wholesale, so the name stays readable and the
 * action is still announced distinctly (TalkBack reads "Home, button, collapse group").
 *
 * Not expressible as a normal Compose/ViewModel unit test — this module's JVM unit tests have
 * no Robolectric or compose-ui-test on the classpath to build/inspect an actual semantics tree
 * (see NavigationWiringTest's own doc for the same constraint) — so this is the same
 * cheapest-check-that-still-discriminates source scan: it fails if the household name's Row
 * goes back to discarding its descendants' semantics, or if the name stops being rendered as
 * real text.
 */
class HouseholdHeaderSemanticsTest {
    private fun read(relativePath: String): String {
        val file = File(findAppModuleRoot(), "src/main/java/dev/scuttle/inventory/$relativePath")
        check(file.exists()) { "Expected source file not found: ${file.absolutePath}" }
        return file.readText()
    }

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

    /** Drops `//`-comment lines so a doc comment is free to name the banned API for
     * future readers (as this file's own fix does) without tripping the code check below. */
    private fun stripLineComments(source: String): String =
        source.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    @Test
    fun `AllStoragesScreen's household header never discards descendant semantics`() {
        val src = read("ui/home/AllStoragesScreen.kt")

        assertFalse(
            "AllStoragesScreen.kt uses clearAndSetSemantics again — the last time it did, it sat on " +
                "the collapsible household header's Row and discarded the household name " +
                "(Text(text = entry.name, ...)) from the semantics tree entirely, breaking both " +
                "TalkBack and every hasText(\"Home\")-driven flow test on this screen.",
            stripLineComments(src).contains("clearAndSetSemantics"),
        )

        assertTrue(
            "AllStoragesScreen no longer renders the household's name as real text (Text(text = " +
                "entry.name, ...)) — a TalkBack user and every hasText(\"<household>\") flow-test " +
                "lookup on this screen both depend on the name actually being in the semantics tree.",
            src.contains("text = entry.name,"),
        )

        assertTrue(
            "The collapse/expand toggle no longer announces its action via onClickLabel — without " +
                "it, the only way to convey \"this row also collapses/expands\" to TalkBack would be " +
                "clearAndSetSemantics again, which is exactly the regression this test guards against.",
            src.contains("onClickLabel = toggleCollapsedCd"),
        )
    }
}
