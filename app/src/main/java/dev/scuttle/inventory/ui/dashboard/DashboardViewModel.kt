package dev.scuttle.inventory.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.LocationStats
import dev.scuttle.inventory.data.LowStockItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    ) : ViewModel() {
        val state: StateFlow<DashboardUiState> =
            store.state
                .map { s ->
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
                        errorRes = s.errorRes,
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

        fun refresh() = store.refresh(userInitiated = true)
    }
