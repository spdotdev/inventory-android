package dev.scuttle.inventory.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationStats(val location: LocationDto, val householdId: Long, val productCount: Int)

data class DashboardUiState(
    val loading: Boolean = false,
    val hasNoHouseholds: Boolean = false,
    val totalLocations: Int = 0,
    val totalShelves: Int = 0,
    val totalProducts: Int = 0,
    val mandatoryWarnings: Int = 0,
    val locationStats: List<LocationStats> = emptyList(),
    val favoriteLocationIds: Set<Long> = emptySet(),
    val favoriteShelfIds: Set<Long> = emptySet(),
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val locationRepository: LocationRepository,
    private val shelfRepository: ShelfRepository,
    private val productRepository: ProductRepository,
    private val favoritesStore: FavoritesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            _state.update { it.copy(
                favoriteLocationIds = favoritesStore.getFavoriteLocations(),
                favoriteShelfIds = favoritesStore.getFavoriteShelves(),
            )}
            runCatching {
                val households = householdRepository.list()
                var totalShelves = 0
                var totalProducts = 0
                var mandatoryWarnings = 0
                val locationStats = mutableListOf<LocationStats>()

                for (hh in households) {
                    val locations = locationRepository.list(hh.id)
                    for (location in locations) {
                        var locationProductCount = 0
                        val shelves = runCatching { shelfRepository.list(hh.id, location.id) }.getOrDefault(emptyList())
                        totalShelves += shelves.size
                        for (shelf in shelves) {
                            val products = runCatching { productRepository.list(hh.id, shelf.id) }.getOrDefault(emptyList())
                            totalProducts += products.size
                            locationProductCount += products.size
                            mandatoryWarnings += products.count { it.is_mandatory == true && it.quantity == 0 }
                        }
                        locationStats.add(LocationStats(location, hh.id, locationProductCount))
                    }
                }

                _state.update {
                    it.copy(
                        loading = false,
                        hasNoHouseholds = households.isEmpty(),
                        totalLocations = locationStats.size,
                        totalShelves = totalShelves,
                        totalProducts = totalProducts,
                        mandatoryWarnings = mandatoryWarnings,
                        locationStats = locationStats,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to load stats.") }
            }
        }
    }

    fun toggleFavoriteLocation(id: Long) {
        favoritesStore.toggleFavoriteLocation(id)
        _state.update { it.copy(favoriteLocationIds = favoritesStore.getFavoriteLocations()) }
    }

    fun toggleFavoriteShelf(id: Long) {
        favoritesStore.toggleFavoriteShelf(id)
        _state.update { it.copy(favoriteShelfIds = favoritesStore.getFavoriteShelves()) }
    }
}
