package dev.scuttle.inventory.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.LocationDeletion
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.ui.hierarchy.DeletePlan
import dev.scuttle.inventory.ui.hierarchy.MoveTarget
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
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
    /** Non-null while the delete-strategy dialog is open for a Home location. */
    val pendingDelete: DeletePlan? = null,
    /**
     * The location's household's other live locations — the ONLY signal for
     * whether the dialog offers "move" (DeletePlan itself carries no canMove
     * flag; see DeletePlan.kt). Empty when the household has no other location
     * to move contents into.
     */
    val moveTargets: List<MoveTarget> = emptyList(),
    /** The batch just deleted, for the Undo snackbar. Cleared once consumed. */
    val lastBatchId: String? = null,
    /**
     * One-shot result of the last undoDelete() call — null while none is pending.
     * The screen turns this into the matching localized snackbar (delete_undone /
     * delete_undo_failed) and calls [DrawerViewModel.consumeUndoResult].
     */
    val undoResult: UndoOutcome? = null,
)

/**
 * The delete-strategy flow's own bit of state, combined with [HierarchyStore]'s
 * state to build [DrawerUiState]. Kept separate from the household/location
 * projection below because it has nothing to do with what HierarchyStore knows —
 * it is local to the dialog Home opens for one location at a time.
 */
private data class DeleteFlowState(
    val pendingDelete: DeletePlan? = null,
    val moveTargets: List<MoveTarget> = emptyList(),
    val lastBatchId: String? = null,
    val undoResult: UndoOutcome? = null,
)

@HiltViewModel
class DrawerViewModel
    @Inject
    constructor(
        private val store: HierarchyStore,
        private val locationRepository: LocationRepository,
        private val restoreRepository: RestoreRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val deleteFlow = MutableStateFlow(DeleteFlowState())

        val state: StateFlow<DrawerUiState> =
            combine(store.state, deleteFlow) { s, del ->
                DrawerUiState(
                    entries = s.entries,
                    locationWarnings = s.locationWarnings,
                    missingItemCount = s.missingItemCount,
                    loading = s.loading,
                    refreshing = s.refreshing,
                    error = s.error,
                    pendingDelete = del.pendingDelete,
                    moveTargets = del.moveTargets,
                    lastBatchId = del.lastBatchId,
                    undoResult = del.undoResult,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

        private var deleteJob: Job? = null

        // Which location the open strategy dialog (or the last completed batch)
        // belongs to. Kept out of DrawerUiState/DeleteFlowState on purpose: the
        // dialog only ever needs the DeletePlan + moveTargets, and Undo only
        // ever needs to replay these two ids against RestoreRepository.
        private var pendingHouseholdId: Long? = null
        private var pendingLocationId: Long? = null
        private var lastBatchHouseholdId: Long? = null

        // One-shot delete failure, surfaced by AllStorages as a snackbar (W10). Kept
        // separate from the store-derived load `error` above because a delete fails
        // while the list is populated — the inline ErrorRetry (empty-state only)
        // would never show it.
        private val _actionError = MutableStateFlow<String?>(null)
        val actionError: StateFlow<String?> = _actionError.asStateFlow()

        init {
            // CRITICAL fix: this VM is resolved once against the Activity's
            // ViewModelStoreOwner (MainActivity's InventoryNavHost) and survives a
            // logout→login in the same process — SessionCleaner.clear() only
            // reaches Hilt @Singletons, not ViewModels. Without this, a pending
            // delete-strategy dialog or "Deleted · Undo" snackbar minted under one
            // account (carrying its household/location/batch ids) would still be
            // showing for the NEXT signed-in account, and confirming/undoing it
            // would fire API calls against the old ids under the new token.
            // authRepository.sessionActive flips on every session boundary (logout,
            // a mid-session 401, and the token being set for a fresh login) — react
            // to every emission, including the first (current-value) one, since a
            // reset at VM-creation time on already-empty state is a harmless no-op.
            viewModelScope.launch {
                authRepository.sessionActive.collect { resetTransientDeleteState() }
            }
        }

        private fun resetTransientDeleteState() {
            deleteJob?.cancel()
            deleteJob = null
            pendingHouseholdId = null
            pendingLocationId = null
            lastBatchHouseholdId = null
            deleteFlow.update { DeleteFlowState() }
        }

        fun refresh() = store.refresh(userInitiated = true)

        /**
         * Opens the delete-strategy dialog for ONE location.
         *
         * Home deletes one location per gesture, unlike StorageOverviewScreen's
         * batch selection — there is no cross-household batch semantics to build
         * here: a MOVE_CONTENTS target has to live in the SAME household as what's
         * being deleted, and a selection spanning households would have no single
         * household to draw those targets from.
         *
         * Reads a FRESH copy of the household's locations from the server first —
         * the same staleness concern StorageOverviewViewModel.requestDelete()
         * guards against: a shelf added via LocationDetailScreen's own add-shelf
         * sheet never updates this location's shelf_count in HierarchyStore's
         * cached `entries` on its own (see LocationDto.shelf_count's own doc
         * comment, which calls out HierarchyStore.refresh() as the fix for a
         * screen that only ever reads HierarchyStore's cache). Going straight to
         * `locationRepository.list()` buys the same freshness guarantee without
         * paying for a full households+shelves+products reload just to open a
         * confirmation dialog.
         */
        fun requestDelete(
            householdId: Long,
            locationId: Long,
        ) {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    runCatching { locationRepository.list(householdId) }
                        .onSuccess { locations ->
                            val location = locations.firstOrNull { it.id == locationId } ?: return@onSuccess
                            pendingHouseholdId = householdId
                            pendingLocationId = locationId
                            deleteFlow.update {
                                it.copy(
                                    pendingDelete =
                                        DeletePlan(
                                            itemCount = 1,
                                            productCount = location.product_count,
                                            // The trap: the server asks about a
                                            // location's SHELVES, not its products
                                            // (see DeletePlan.kt) — a location with
                                            // only empty shelves still needs a
                                            // strategy. Do not "simplify" this to
                                            // productCount.
                                            contentCount = location.shelf_count,
                                        ),
                                    moveTargets =
                                        locations
                                            .filter { l -> l.id != locationId }
                                            .map { l -> MoveTarget(id = l.id, name = l.name) },
                                )
                            }
                        }.onFailure { e -> _actionError.value = e.toUserMessage("Failed to delete location.") }
                }
        }

        fun confirmDelete(
            strategy: LocationDeleteStrategy?,
            targetId: Long?,
        ) {
            val householdId = pendingHouseholdId
            val locationId = pendingLocationId
            if (householdId == null || locationId == null || deleteJob?.isActive == true) return

            // Client-minted, one id for the whole gesture — Home only ever deletes
            // one location per gesture, but the id still has to exist for Undo to
            // find this delete again (the server requires deletion_batch_id on
            // every delete, empty container or not).
            val batchId = UUID.randomUUID().toString()

            deleteJob =
                viewModelScope.launch {
                    runCatching {
                        locationRepository.deleteWithStrategy(
                            householdId,
                            locationId,
                            LocationDeletion(batchId, strategy, targetId),
                        )
                    }.onSuccess {
                        lastBatchHouseholdId = householdId
                        deleteFlow.update {
                            it.copy(pendingDelete = null, moveTargets = emptyList(), lastBatchId = batchId)
                        }
                        store.refresh()
                    }.onFailure { e ->
                        deleteFlow.update { it.copy(pendingDelete = null, moveTargets = emptyList()) }
                        _actionError.value = e.toUserMessage("Failed to delete location.")
                    }
                    pendingHouseholdId = null
                    pendingLocationId = null
                }
        }

        fun cancelDelete() {
            pendingHouseholdId = null
            pendingLocationId = null
            deleteFlow.update { it.copy(pendingDelete = null, moveTargets = emptyList()) }
        }

        fun undoDelete() {
            val householdId = lastBatchHouseholdId
            val batchId = deleteFlow.value.lastBatchId
            if (householdId == null || batchId == null || deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    // A 409 here means the batch was already restored (another
                    // device, a double-tap) or permanently removed past the undo
                    // window — NOT a generic action failure, so this does NOT go
                    // through _actionError/toUserMessage's generic fallback; the
                    // screen turns undoResult into the specific message instead.
                    runCatching { restoreRepository.restore(householdId, batchId) }
                        .onSuccess {
                            deleteFlow.update { it.copy(lastBatchId = null, undoResult = UndoOutcome.SUCCESS) }
                            store.refresh()
                        }.onFailure {
                            deleteFlow.update { it.copy(undoResult = UndoOutcome.FAILURE) }
                        }
                }
        }

        fun consumeLastBatch() = deleteFlow.update { it.copy(lastBatchId = null) }

        fun consumeUndoResult() = deleteFlow.update { it.copy(undoResult = null) }

        fun consumeActionError() {
            _actionError.value = null
        }

        fun reportLocationWarning(
            locationId: Long,
            hasWarning: Boolean,
        ) = store.reportLocationWarning(locationId, hasWarning)
    }
