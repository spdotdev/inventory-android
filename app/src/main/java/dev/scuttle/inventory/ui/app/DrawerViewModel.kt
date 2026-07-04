package dev.scuttle.inventory.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.error.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // Surfaced from HierarchyStore so AllStorages can tell a real network failure
    // apart from a genuinely empty account (W3) — without this a failed load
    // rendered the "No storages yet" empty state.
    val error: String? = null,
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
            error = s.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

    private var deleteJob: Job? = null

    // One-shot delete failure, surfaced by AllStorages as a snackbar (W10). Kept
    // separate from the store-derived load `error` above because a delete fails
    // while the list is populated — the inline ErrorRetry (empty-state only)
    // would never show it.
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun refresh() = store.refresh(userInitiated = true)

    fun deleteLocation(householdId: Long, locationId: Long) {
        if (deleteJob?.isActive == true) return
        deleteJob = viewModelScope.launch {
            runCatching { locationRepository.delete(householdId, locationId) }
                .onSuccess { store.refresh() }
                .onFailure { e -> _actionError.value = e.toUserMessage("Failed to delete location.") }
        }
    }

    fun consumeActionError() { _actionError.value = null }

    fun reportLocationWarning(locationId: Long, hasWarning: Boolean) =
        store.reportLocationWarning(locationId, hasWarning)
}
