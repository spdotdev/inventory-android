package dev.scuttle.inventory

import dev.scuttle.inventory.ui.theme.HOUSEHOLD_ACCENT_COUNT
import dev.scuttle.inventory.ui.theme.HOUSEHOLD_ICON_COUNT
import dev.scuttle.inventory.ui.theme.householdAccentIndex
import dev.scuttle.inventory.ui.theme.householdIconIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic per-household accent/icon index math (Compose-free). */
class HouseholdThemeIndexTest {
    @Test
    fun indices_are_always_within_the_table_bounds() {
        for (id in 0L..200L) {
            assertTrue(householdAccentIndex(id) in 0 until HOUSEHOLD_ACCENT_COUNT)
            assertTrue(householdIconIndex(id) in 0 until HOUSEHOLD_ICON_COUNT)
        }
    }

    @Test
    fun the_same_id_always_maps_to_the_same_indices() {
        assertEquals(householdAccentIndex(42), householdAccentIndex(42))
        assertEquals(householdIconIndex(42), householdIconIndex(42))
    }

    @Test
    fun accent_and_icon_do_not_move_in_lockstep_for_small_ids() {
        // If they used the same stride, accent and icon would be equal for every
        // id; the different icon stride must break that for at least some ids.
        val differ = (1L..8L).count { householdAccentIndex(it) != householdIconIndex(it) }
        assertTrue("expected decorrelation across ids 1..8, got $differ", differ >= 6)
    }
}
