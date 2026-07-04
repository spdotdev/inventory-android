package dev.scuttle.inventory.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrawerUiState(
    val entries: List<HouseholdWithLocations> = emptyList(),
    val locationWarnings: Map<Long, Boolean> = emptyMap(),
    val missingItemCount: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val store: HierarchyStore,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    val state: StateFlow<DrawerUiState> = store.state.map { s ->
        DrawerUiState(
            entries = s.entries,
            locationWarnings = s.locationWarnings,
            missingItemCount = s.missingItemCount,
            loading = s.loading,
            refreshing = s.refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

    private var deleteJob: Job? = null

    fun refresh() = store.refresh(userInitiated = true)

    fun deleteLocation(householdId: Long, locationId: Long) {
        if (deleteJob?.isActive == true) return
        deleteJob = viewModelScope.launch {
            runCatching { locationRepository.delete(householdId, locationId) }.onSuccess { store.refresh() }
        }
    }

    fun reportLocationWarning(locationId: Long, hasWarning: Boolean) =
        store.reportLocationWarning(locationId, hasWarning)
}
