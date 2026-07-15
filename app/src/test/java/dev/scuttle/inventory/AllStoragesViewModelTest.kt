package dev.scuttle.inventory

import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.data.settings.HouseholdViewStore
import dev.scuttle.inventory.ui.home.AllStoragesViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllStoragesViewModelTest {
    private class FakeFavoritesStore(
        initialLocations: Set<Long> = emptySet(),
    ) : FavoritesStore {
        private val locations = initialLocations.toMutableSet()
        private val shelves = mutableSetOf<Long>()

        override fun getFavoriteLocations() = locations.toSet()

        override fun toggleFavoriteLocation(id: Long) {
            if (!locations.add(id)) locations.remove(id)
        }

        override fun isFavoriteLocation(id: Long) = id in locations

        override fun getFavoriteShelves() = shelves.toSet()

        override fun toggleFavoriteShelf(id: Long) {
            if (!shelves.add(id)) shelves.remove(id)
        }

        override fun isFavoriteShelf(id: Long) = id in shelves
    }

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
    fun initial_state_loads_favorites_from_store() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(initialLocations = setOf(10L)), FakeHouseholdViewStore())
        assertTrue(10L in vm.state.value.favoriteLocationIds)
    }

    @Test
    fun toggle_adds_favorite_when_not_set() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(), FakeHouseholdViewStore())
        vm.toggleFavorite(10L)
        assertTrue(10L in vm.state.value.favoriteLocationIds)
    }

    @Test
    fun toggle_removes_favorite_when_already_set() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(initialLocations = setOf(10L)), FakeHouseholdViewStore())
        vm.toggleFavorite(10L)
        assertFalse(10L in vm.state.value.favoriteLocationIds)
    }

    @Test
    fun toggle_is_idempotent_add_remove_add() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(), FakeHouseholdViewStore())
        vm.toggleFavorite(5L)
        assertTrue(5L in vm.state.value.favoriteLocationIds)
        vm.toggleFavorite(5L)
        assertFalse(5L in vm.state.value.favoriteLocationIds)
        vm.toggleFavorite(5L)
        assertTrue(5L in vm.state.value.favoriteLocationIds)
    }

    @Test
    fun collapsing_a_household_persists() =
        runTest {
            val store = FakeHouseholdViewStore()
            val viewModel = AllStoragesViewModel(FakeFavoritesStore(), store)

            viewModel.toggleCollapsed(1L)

            assertTrue(1L in viewModel.state.value.collapsedHouseholdIds)
            assertTrue(1L in store.collapsed())
        }

    @Test
    fun initial_state_loads_collapsed_ids_from_store() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(), FakeHouseholdViewStore(initialCollapsed = setOf(2L)))
        assertTrue(2L in vm.state.value.collapsedHouseholdIds)
    }

    @Test
    fun expanding_a_collapsed_household_persists() {
        val store = FakeHouseholdViewStore(initialCollapsed = setOf(3L))
        val vm = AllStoragesViewModel(FakeFavoritesStore(), store)

        vm.toggleCollapsed(3L)

        assertFalse(3L in vm.state.value.collapsedHouseholdIds)
        assertFalse(3L in store.collapsed())
    }

    @Test
    fun collapsing_one_household_does_not_collapse_another() {
        // A naive "single collapsed id" or boolean implementation would make every
        // group collapse together -- collapse must be keyed PER household.
        val vm = AllStoragesViewModel(FakeFavoritesStore(), FakeHouseholdViewStore())

        vm.toggleCollapsed(1L)

        assertTrue(1L in vm.state.value.collapsedHouseholdIds)
        assertFalse(2L in vm.state.value.collapsedHouseholdIds)
    }

    @Test
    fun collapsing_a_household_does_not_touch_favorites_or_other_households() {
        // Collapsing is a pure presentation toggle: it must never mutate anything
        // that could hide or lose a selection/favorite the user made elsewhere.
        // Asserts the favorite set is EXACTLY unchanged (not just "still contains
        // the original entry") -- a mutation that also toggled the household id
        // into favorites would slip past a weaker "still contains 7L" check.
        val vm =
            AllStoragesViewModel(
                FakeFavoritesStore(initialLocations = setOf(7L)),
                FakeHouseholdViewStore(initialCollapsed = setOf(2L)),
            )

        vm.toggleCollapsed(1L)

        assertEquals(setOf(7L), vm.state.value.favoriteLocationIds)
        assertEquals(setOf(1L, 2L), vm.state.value.collapsedHouseholdIds)
    }

    @Test
    fun ordered_entries_falls_back_to_name_when_no_stored_order() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(), FakeHouseholdViewStore())
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
        val vm =
            AllStoragesViewModel(
                FakeFavoritesStore(),
                FakeHouseholdViewStore(initialOrder = listOf(2L, 1L)),
            )
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
