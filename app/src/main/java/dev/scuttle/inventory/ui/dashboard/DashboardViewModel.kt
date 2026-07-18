package dev.scuttle.inventory.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.LocationStats
import dev.scuttle.inventory.data.LowStockItem
import dev.scuttle.inventory.data.ShelfEntry
import dev.scuttle.inventory.data.settings.FavoritesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Identity of a household, as the dashboard needs it: enough to name and theme it. */
data class DashboardHousehold(
    val id: Long,
    val name: String,
    val color: String?,
    val icon: String?,
)

/** A household and the locations belonging to it, for the grouped chart. */
data class HouseholdLocationGroup(
    val household: DashboardHousehold,
    val stats: List<LocationStats>,
)

data class DashboardUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val hasNoHouseholds: Boolean = false,
    val firstHouseholdId: Long? = null,
    val households: List<DashboardHousehold> = emptyList(),
    val totalLocations: Int = 0,
    val totalShelves: Int = 0,
    val totalProducts: Int = 0,
    val mandatoryWarnings: Int = 0,
    val lowStockItems: List<LowStockItem> = emptyList(),
    val locationStats: List<LocationStats> = emptyList(),
    val favoriteLocationIds: Set<Long> = emptySet(),
    val favoriteShelfIds: Set<Long> = emptySet(),
    val favoriteShelves: List<ShelfEntry> = emptyList(),
    // H3: an R.string.* id, not a raw literal — resolved via stringResource() by the screen.
    val errorRes: Int? = null,
) {
    /**
     * The dashboard is the one screen that aggregates across households (#33), so it
     * is the one screen that has to say which household a row belongs to. With a
     * single household there is nothing to disambiguate — stay silent rather than
     * decorate every row with the same badge.
     */
    val showHouseholdAttribution: Boolean get() = households.size > 1

    /** Locations grouped under their household, in the order the store lists them. */
    val groupedLocationStats: List<HouseholdLocationGroup> get() =
        households.mapNotNull { household ->
            locationStats
                .filter { it.householdId == household.id }
                .takeIf { it.isNotEmpty() }
                ?.let { HouseholdLocationGroup(household, it) }
        }

    /**
     * Bar scale for the chart, taken across **all** households. Scaling each group to
     * its own max would draw a 2-product household's bar as full as a 9-product one's.
     */
    val maxLocationProductCount: Int get() = locationStats.maxOfOrNull { it.productCount } ?: 0

    fun householdFor(id: Long): DashboardHousehold? = households.firstOrNull { it.id == id }
}

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val store: HierarchyStore,
        private val favoritesStore: FavoritesStore,
    ) : ViewModel() {
        private val favState =
            MutableStateFlow(
                favoritesStore.getFavoriteLocations() to favoritesStore.getFavoriteShelves(),
            )

        val state: StateFlow<DashboardUiState> =
            combine(store.state, favState) { s, (favLocs, favShelves) ->
                DashboardUiState(
                    loading = s.loading,
                    refreshing = s.refreshing,
                    hasNoHouseholds = s.entries.isEmpty() && !s.loading,
                    firstHouseholdId = s.entries.firstOrNull()?.id,
                    households =
                        s.entries.map {
                            DashboardHousehold(id = it.id, name = it.name, color = it.color, icon = it.icon)
                        },
                    totalLocations = s.locationStats.size,
                    totalShelves = s.totalShelves,
                    totalProducts = s.totalProducts,
                    mandatoryWarnings = s.mandatoryWarnings,
                    lowStockItems = s.lowStockItems,
                    locationStats = s.locationStats,
                    favoriteLocationIds = favLocs,
                    favoriteShelfIds = favShelves,
                    favoriteShelves = s.allShelves.filter { it.shelf.id in favShelves },
                    errorRes = s.errorRes,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

        fun refresh() = store.refresh(userInitiated = true)

        fun toggleFavoriteLocation(id: Long) {
            favoritesStore.toggleFavoriteLocation(id)
            favState.update { favoritesStore.getFavoriteLocations() to favoritesStore.getFavoriteShelves() }
        }

        fun toggleFavoriteShelf(id: Long) {
            favoritesStore.toggleFavoriteShelf(id)
            favState.update { favoritesStore.getFavoriteLocations() to favoritesStore.getFavoriteShelves() }
        }
    }
