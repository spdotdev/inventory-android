package dev.scuttle.inventory

import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.settings.HouseholdViewStore
import dev.scuttle.inventory.ui.home.AllStoragesViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllStoragesViewModelTest {
    private class FakeHouseholdViewStore(
        initialCollapsed: Set<Long> = emptySet(),
        initialOrder: List<Long> = emptyList(),
    ) : HouseholdViewStore {
        private val collapsedIds = initialCollapsed.toMutableSet()
        private var storedOrder = initialOrder
        var cleared = false

        override fun collapsed(): Set<Long> = collapsedIds.toSet()

        override fun toggleCollapsed(id: Long) {
            if (!collapsedIds.add(id)) collapsedIds.remove(id)
        }

        override fun order(): List<Long> = storedOrder

        override fun setOrder(ids: List<Long>) {
            storedOrder = ids
        }

        override fun clear() {
            cleared = true
            collapsedIds.clear()
            storedOrder = emptyList()
        }
    }

    @Test
    fun collapsing_a_household_persists() =
        runTest {
            val store = FakeHouseholdViewStore()
            val viewModel = AllStoragesViewModel(store)

            viewModel.toggleCollapsed(1L)

            assertTrue(1L in viewModel.state.value.collapsedHouseholdIds)
            assertTrue(1L in store.collapsed())
        }

    @Test
    fun initial_state_loads_collapsed_ids_from_store() {
        val vm = AllStoragesViewModel(FakeHouseholdViewStore(initialCollapsed = setOf(2L)))
        assertTrue(2L in vm.state.value.collapsedHouseholdIds)
    }

    @Test
    fun expanding_a_collapsed_household_persists() {
        val store = FakeHouseholdViewStore(initialCollapsed = setOf(3L))
        val vm = AllStoragesViewModel(store)

        vm.toggleCollapsed(3L)

        assertFalse(3L in vm.state.value.collapsedHouseholdIds)
        assertFalse(3L in store.collapsed())
    }

    @Test
    fun collapsing_one_household_does_not_collapse_another() {
        // A naive "single collapsed id" or boolean implementation would make every
        // group collapse together -- collapse must be keyed PER household.
        val vm = AllStoragesViewModel(FakeHouseholdViewStore())

        vm.toggleCollapsed(1L)

        assertTrue(1L in vm.state.value.collapsedHouseholdIds)
        assertFalse(2L in vm.state.value.collapsedHouseholdIds)
    }

    @Test
    fun ordered_entries_falls_back_to_name_when_no_stored_order() {
        val vm = AllStoragesViewModel(FakeHouseholdViewStore())
        val entries =
            listOf(
                HouseholdWithLocations(id = 2L, name = "Office", locations = emptyList()),
                HouseholdWithLocations(id = 1L, name = "Home", locations = emptyList()),
            )

        val ordered = vm.orderedEntries(entries)

        assertEquals(listOf("Home", "Office"), ordered.map { it.name })
    }

    @Test
    fun ordered_entries_honors_stored_order_over_name() {
        val vm = AllStoragesViewModel(FakeHouseholdViewStore(initialOrder = listOf(2L, 1L)))
        val entries =
            listOf(
                HouseholdWithLocations(id = 1L, name = "Home", locations = emptyList()),
                HouseholdWithLocations(id = 2L, name = "Office", locations = emptyList()),
            )

        val ordered = vm.orderedEntries(entries)

        // Office (stored position 0) before Home (stored position 1) -- drag order
        // wins over the alphabetical tie-break, exactly as HierarchyOrder mandates.
        assertEquals(listOf("Office", "Home"), ordered.map { it.name })
    }
}
