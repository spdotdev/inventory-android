package dev.scuttle.inventory.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
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
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DrawerUiState())
    val state: StateFlow<DrawerUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val households = householdRepository.list()
                val entries = households.map { hh ->
                    val locations = runCatching { locationRepository.list(hh.id) }.getOrDefault(emptyList())
                    HouseholdWithLocations(hh.id, hh.name, locations)
                }
                _state.update { it.copy(entries = entries) }
            }
        }
    }

    fun deleteLocation(householdId: Long, locationId: Long) {
        viewModelScope.launch {
            runCatching { locationRepository.delete(householdId, locationId) }.onSuccess { refresh() }
        }
    }
}
