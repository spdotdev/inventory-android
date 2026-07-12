package dev.scuttle.inventory

import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.ui.home.AllStoragesViewModel
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

    @Test
    fun initial_state_loads_favorites_from_store() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(initialLocations = setOf(10L)))
        assertTrue(10L in vm.state.value.favoriteLocationIds)
    }

    @Test
    fun toggle_adds_favorite_when_not_set() {
        val vm = AllStoragesViewModel(FakeFavoritesStore())
        vm.toggleFavorite(10L)
        assertTrue(10L in vm.state.value.favoriteLocationIds)
    }

    @Test
    fun toggle_removes_favorite_when_already_set() {
        val vm = AllStoragesViewModel(FakeFavoritesStore(initialLocations = setOf(10L)))
        vm.toggleFavorite(10L)
        assertFalse(10L in vm.state.value.favoriteLocationIds)
    }

    @Test
    fun toggle_is_idempotent_add_remove_add() {
        val vm = AllStoragesViewModel(FakeFavoritesStore())
        vm.toggleFavorite(5L)
        assertTrue(5L in vm.state.value.favoriteLocationIds)
        vm.toggleFavorite(5L)
        assertFalse(5L in vm.state.value.favoriteLocationIds)
        vm.toggleFavorite(5L)
        assertTrue(5L in vm.state.value.favoriteLocationIds)
    }
}
