package dev.scuttle.inventory

import dev.scuttle.inventory.ui.hierarchy.DeletePlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletePlanTest {
    @Test
    fun an_empty_selection_needs_no_strategy() {
        val plan = DeletePlan(itemCount = 2, productCount = 0, contentCount = 0, hasOtherTargets = true)

        // Nothing to rescue — a plain confirm is enough.
        assertFalse(plan.needsStrategy)
    }

    @Test
    fun a_shelf_holding_products_needs_a_strategy() {
        // For a SHELF, contentCount is its product_count.
        val plan = DeletePlan(itemCount = 1, productCount = 12, contentCount = 12, hasOtherTargets = true)

        assertTrue(plan.needsStrategy)
    }

    @Test
    fun a_location_holding_an_EMPTY_shelf_still_needs_a_strategy() {
        // THE cross-repo trap. The server asks for a strategy when a location has
        // SHELVES, not when it has products — so a location with one empty shelf
        // (0 products, 1 shelf) must still prompt. Deciding this from productCount
        // would send a strategy-less delete and 422 every single time.
        val plan = DeletePlan(itemCount = 1, productCount = 0, contentCount = 1, hasOtherTargets = true)

        assertTrue(plan.needsStrategy)
    }

    @Test
    fun move_is_unavailable_when_there_is_nowhere_to_move_to() {
        // Deleting the household's only location: there is no other location to
        // take the contents, so the UI must not offer "move" as a dead option.
        val plan = DeletePlan(itemCount = 1, productCount = 12, contentCount = 12, hasOtherTargets = false)

        assertTrue(plan.needsStrategy)
        assertFalse(plan.canMove)
    }

    @Test
    fun move_is_available_when_a_target_exists() {
        val plan = DeletePlan(itemCount = 1, productCount = 12, contentCount = 12, hasOtherTargets = true)

        assertTrue(plan.canMove)
    }
}
