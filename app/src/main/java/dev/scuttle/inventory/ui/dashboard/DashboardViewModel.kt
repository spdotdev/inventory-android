package dev.scuttle.inventory.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.LocationStats
import dev.scuttle.inventory.data.LowStockItem
import dev.scuttle.inventory.data.ShelfEntry
import dev.scuttle.inventory.data.settings.FavoritesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val hasNoHouseholds: Boolean = false,
    val totalLocations: Int = 0,
    val totalShelves: Int = 0,
    val totalProducts: Int = 0,
    val mandatoryWarnings: Int = 0,
    val lowStockItems: List<LowStockItem> = emptyList(),
    val locationStats: List<LocationStats> = emptyList(),
    val favoriteLocationIds: Set<Long> = emptySet(),
    val favoriteShelfIds: Set<Long> = emptySet(),
    val favoriteShelves: List<ShelfEntry> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val store: HierarchyStore,
    private val favoritesStore: FavoritesStore,
) : ViewModel() {

    private val _favState = MutableStateFlow(
        favoritesStore.getFavoriteLocations() to favoritesStore.getFavoriteShelves(),
    )

    val state: StateFlow<DashboardUiState> = combine(store.state, _favState) { s, (favLocs, favShelves) ->
        DashboardUiState(
            loading = s.loading,
            refreshing = s.refreshing,
            hasNoHouseholds = s.entries.isEmpty() && !s.loading,
            totalLocations = s.locationStats.size,
            totalShelves = s.totalShelves,
            totalProducts = s.totalProducts,
            mandatoryWarnings = s.mandatoryWarnings,
            lowStockItems = s.lowStockItems,
            locationStats = s.locationStats,
            favoriteLocationIds = favLocs,
            favoriteShelfIds = favShelves,
            favoriteShelves = s.allShelves.filter { it.shelf.id in favShelves },
            error = s.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    fun refresh() = store.refresh(userInitiated = true)

    fun toggleFavoriteLocation(id: Long) {
        favoritesStore.toggleFavoriteLocation(id)
        _favState.update { favoritesStore.getFavoriteLocations() to favoritesStore.getFavoriteShelves() }
    }

    fun toggleFavoriteShelf(id: Long) {
        favoritesStore.toggleFavoriteShelf(id)
        _favState.update { favoritesStore.getFavoriteLocations() to favoritesStore.getFavoriteShelves() }
    }
}
