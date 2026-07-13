package dev.scuttle.inventory

import dev.scuttle.inventory.ui.hierarchy.DeletePlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletePlanTest {
    @Test
    fun an_empty_selection_needs_no_strategy() {
        val plan = DeletePlan(itemCount = 2, productCount = 0, contentCount = 0)

        // Nothing to rescue — a plain confirm is enough.
        assertFalse(plan.needsStrategy)
    }

    @Test
    fun a_shelf_holding_products_needs_a_strategy() {
        // For a SHELF, contentCount is its product_count.
        val plan = DeletePlan(itemCount = 1, productCount = 12, contentCount = 12)

        assertTrue(plan.needsStrategy)
    }

    @Test
    fun a_location_holding_an_EMPTY_shelf_still_needs_a_strategy() {
        // THE cross-repo trap. The server asks for a strategy when a location has
        // SHELVES, not when it has products — so a location with one empty shelf
        // (0 products, 1 shelf) must still prompt. Deciding this from productCount
        // would send a strategy-less delete and 422 every single time.
        val plan = DeletePlan(itemCount = 1, productCount = 0, contentCount = 1)

        assertTrue(plan.needsStrategy)
    }

    // "Never offer move when there is nowhere to move to" is NOT tested here:
    // DeletePlan no longer carries that signal. The dialog decides it from the
    // target list it is actually handed, so the rule is pinned where it lives —
    // DeleteStrategyDialogTest.move_option_is_not_offered_when_there_are_no_targets.
}
