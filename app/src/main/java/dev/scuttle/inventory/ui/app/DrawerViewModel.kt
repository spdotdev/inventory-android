package dev.scuttle.inventory.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdWithLocations(
    val id: Long,
    val name: String,
    val locations: List<LocationDto>,
)

data class DrawerUiState(
    val entries: List<HouseholdWithLocations> = emptyList(),
    val locationWarnings: Map<Long, Boolean> = emptyMap(),
    val missingItemCount: Int = 0,
    val loading: Boolean = false,
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val locationRepository: LocationRepository,
    private val shelfRepository: ShelfRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DrawerUiState())
    val state: StateFlow<DrawerUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val households = householdRepository.list()
                var missingItemCount = 0
                val entries = households.map { hh ->
                    val locations = runCatching { locationRepository.list(hh.id) }.getOrDefault(emptyList())
                    for (location in locations) {
                        val shelves = runCatching { shelfRepository.list(hh.id, location.id) }.getOrDefault(emptyList())
                        for (shelf in shelves) {
                            val products = runCatching { productRepository.list(hh.id, shelf.id) }.getOrDefault(emptyList())
                            missingItemCount += products.count { it.is_mandatory && it.quantity == 0 }
                        }
                    }
                    HouseholdWithLocations(hh.id, hh.name, locations)
                }
                _state.update { it.copy(entries = entries, missingItemCount = missingItemCount, loading = false) }
            }.onFailure {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun deleteLocation(householdId: Long, locationId: Long) {
        viewModelScope.launch {
            runCatching { locationRepository.delete(householdId, locationId) }.onSuccess { refresh() }
        }
    }

    fun reportLocationWarning(locationId: Long, hasWarning: Boolean) {
        _state.update { state ->
            state.copy(locationWarnings = state.locationWarnings + (locationId to hasWarning))
        }
    }
}
