package dev.scuttle.inventory.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.error.toUserMessageRes
import dev.scuttle.inventory.data.location.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Server-side location name column limit — mirrors StorageOverviewViewModel's own constant. */
private const val MAX_LOCATION_NAME_LENGTH = 50

data class DrawerUiState(
    val entries: List<HouseholdWithLocations> = emptyList(),
    val locationWarnings: Map<Long, Boolean> = emptyMap(),
    /**
     * Per location, the shelf holding its first missing item — the shelf the
     * location screen's pager should open on when the storage tile shows a
     * stock warning (which is itself derived from the same missing items).
     */
    val warningShelfByLocation: Map<Long, Long> = emptyMap(),
    val missingItemCount: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    // Surfaced from HierarchyStore so AllStorages can tell a real network failure
    // apart from a genuinely empty account (W3) — without this a failed load
    // rendered the "No storages yet" empty state. H3: an R.string.* id, not a raw literal.
    val errorRes: Int? = null,
)

/**
 * Home/AllStorages is now a browse-only list — editing (add/rename/delete/
 * reorder) moved entirely to the per-household Storage overview screen
 * (StorageOverviewViewModel), which already supports it. This VM keeps only
 * what AllStoragesScreen and LocationDetailScreen still read: the projected
 * household/location list, stock-warning tracking, and (new) the ability to
 * create a location from the FAB's add-storage sheet on THIS screen.
 */
@HiltViewModel
class DrawerViewModel
    @Inject
    constructor(
        private val store: HierarchyStore,
        private val locationRepository: LocationRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        val state: StateFlow<DrawerUiState> =
            store.state
                .map { s ->
                    DrawerUiState(
                        entries = s.entries,
                        locationWarnings = s.locationWarnings,
                        warningShelfByLocation =
                            s.missingItems
                                .groupBy { it.locationId }
                                .mapValues { (_, items) -> items.first().shelfId },
                        missingItemCount = s.missingItemCount,
                        loading = s.loading,
                        refreshing = s.refreshing,
                        errorRes = s.errorRes,
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

        // One-shot create-location failure, surfaced by AllStorages as a snackbar —
        // same convention as the delete-flow's old actionErrorRes (W10).
        private val _actionErrorRes = MutableStateFlow<Int?>(null)
        val actionErrorRes: StateFlow<Int?> = _actionErrorRes.asStateFlow()

        private var createJob: Job? = null

        init {
            // CRITICAL: this VM is resolved once against the Activity's
            // ViewModelStoreOwner and survives a logout->login in the same process —
            // SessionCleaner.clear() only reaches Hilt @Singletons, not ViewModels.
            // Without this, a failed create's one-shot error minted under one
            // account would still be showing for the NEXT signed-in account.
            viewModelScope.launch {
                authRepository.sessionActive.collect { _actionErrorRes.value = null }
            }
        }

        fun refresh() = store.refresh(userInitiated = true)

        fun reportLocationWarning(
            locationId: Long,
            hasWarning: Boolean,
        ) = store.reportLocationWarning(locationId, hasWarning)

        fun consumeActionError() {
            _actionErrorRes.value = null
        }

        /**
         * Creates a location from AllStoragesScreen's FAB add-storage sheet —
         * the Home-level equivalent of StorageOverviewViewModel.create(), reusing
         * the same repository call. Home has no single household's location list
         * cached the way StorageOverviewViewModel does, so on success this just
         * asks [HierarchyStore] to refresh from server truth rather than trying
         * to splice the new location into local state itself.
         */
        fun createLocation(
            householdId: Long,
            name: String,
            type: String,
            color: String? = null,
            icon: String? = null,
        ) {
            val trimmed = name.trim().take(MAX_LOCATION_NAME_LENGTH)
            if (trimmed.isEmpty() || createJob?.isActive == true) return
            createJob =
                viewModelScope.launch {
                    val result = runCatching { locationRepository.create(householdId, trimmed, type, color, icon) }
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    result
                        .onSuccess { store.refresh() }
                        .onFailure { e ->
                            _actionErrorRes.value = e.toUserMessageRes(R.string.error_generic)
                        }
                }
        }
    }
