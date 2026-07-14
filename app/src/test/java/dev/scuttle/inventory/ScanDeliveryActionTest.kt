package dev.scuttle.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards Task 7's Critical 2 fix: the scanner route used to have exactly one
 * delivery contract (write `scanned_code` to the previous back-stack entry's
 * savedStateHandle and pop), inferred implicitly from "whoever opened me." That
 * broke the moment a second caller (the bottom-bar Scan tab) started opening the
 * same route with no shelf screen underneath it — scanning silently did nothing.
 *
 * [ScannerMode] makes the caller's intent an explicit route argument instead of
 * something inferred from the back stack, and [scanDeliveryActionFor] is the pure
 * decision table keyed on it — both unit-testable with no NavController involved.
 */
class ScanDeliveryActionTest {
    @Test
    fun mode_add_delivers_to_caller_via_savedStateHandle() {
        val action = scanDeliveryActionFor(ScannerMode.ADD, "0512345678900")
        assertEquals(ScanDeliveryAction.DeliverToCaller("0512345678900"), action)
    }

    @Test
    fun mode_lookup_navigates_to_search() {
        val action = scanDeliveryActionFor(ScannerMode.LOOKUP, "0512345678900")
        assertEquals(ScanDeliveryAction.NavigateToSearch("0512345678900"), action)
    }

    /**
     * The two branches must never collapse onto the same behavior — that is
     * exactly how Critical 2 shipped (a single hard-coded delivery for both
     * callers). Asserting they differ, not just that each individually matches
     * its own expectation, is what would catch that regression coming back.
     */
    @Test
    fun add_and_lookup_never_produce_the_same_action() {
        val code = "0512345678900"
        val addAction = scanDeliveryActionFor(ScannerMode.ADD, code)
        val lookupAction = scanDeliveryActionFor(ScannerMode.LOOKUP, code)

        assert(addAction != lookupAction) {
            "ADD and LOOKUP produced the same ScanDeliveryAction: $addAction — the two callers " +
                "would be indistinguishable again, which is exactly the bug this route argument fixes."
        }
    }

    @Test
    fun from_parses_add() {
        assertEquals(ScannerMode.ADD, ScannerMode.from("add"))
    }

    @Test
    fun from_parses_lookup() {
        assertEquals(ScannerMode.LOOKUP, ScannerMode.from("lookup"))
    }

    @Test
    fun from_defaults_to_add_when_null() {
        // Matches the SCANNER route's own NavType default (ScannerMode.ADD.argValue),
        // preserving the original single-caller behavior for anything that reaches
        // this composable without an explicit mode.
        assertEquals(ScannerMode.ADD, ScannerMode.from(null))
    }

    @Test
    fun from_defaults_to_add_when_unrecognized() {
        assertEquals(ScannerMode.ADD, ScannerMode.from("bogus"))
    }

    // --- scannerRouteIsTheBottomBarTab (Minor 9, final review) -----------------
    //
    // Routes.SCANNER ("scanner?mode={mode}") is one route PATTERN for two callers:
    // NavController's own destination.route is identical for both, so the bottom
    // bar's old `currentRoute == tab.route` check couldn't tell LOOKUP (the Scan
    // tab's own destination) apart from ADD (opened from a shelf screen, no bottom
    // bar underneath it) — the bar resurfaced over the camera in ADD mode, with
    // Scan shown selected. This is the pure decision the bar's visibility/selected
    // checks are now built from.

    @Test
    fun lookup_mode_is_the_bottom_bar_tab() {
        assertTrue(scannerRouteIsTheBottomBarTab(ScannerMode.LOOKUP))
    }

    @Test
    fun add_mode_is_not_the_bottom_bar_tab() {
        // The exact regression: ADD (opened from LocationDetailScreen's scan FAB)
        // must NOT be treated as the Scan tab's own destination.
        assertFalse(scannerRouteIsTheBottomBarTab(ScannerMode.ADD))
    }

    @Test
    fun a_null_mode_is_not_the_bottom_bar_tab() {
        // Null means "not currently on the SCANNER route at all" (the caller only
        // resolves a mode when currentRoute == Routes.SCANNER) — must not default
        // to true and show the bar on every unrelated screen.
        assertFalse(scannerRouteIsTheBottomBarTab(null))
    }
}
