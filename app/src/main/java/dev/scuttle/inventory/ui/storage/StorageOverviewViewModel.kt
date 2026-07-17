package dev.scuttle.inventory.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.LocationDeletion
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.ui.common.orderByPosition
import dev.scuttle.inventory.ui.hierarchy.DeletePlan
import dev.scuttle.inventory.ui.hierarchy.MoveTarget
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

val STORAGE_TYPES = listOf("freezer", "fridge", "pantry", "other")

/** Server-side location name column limit — clamped here (rename) and in onNewNameChange (create). */
private const val MAX_LOCATION_NAME_LENGTH = 50

data class StorageOverviewUiState(
    val loading: Boolean = false,
    // Only a user-initiated refresh() flips this — create/delete use `loading` alone,
    // so the pull-to-refresh spinner no longer fires on every mutation.
    val refreshing: Boolean = false,
    val locations: List<LocationDto> = emptyList(),
    val newName: String = "",
    val newType: String = "freezer",
    val error: String? = null,
    val editMode: Boolean = false,
    val selected: Set<Long> = emptySet(),
    /**
     * Mirrors this household's [dev.scuttle.inventory.data.HouseholdWithLocations.canRestructure]
     * (itself HouseholdDto.can_restructure) — gates the top-bar edit pencil so a
     * Member never opens rename/reorder/delete affordances every mutating
     * LocationController route already 403s for them server-side. Defaults true
     * so the pencil isn't hidden for the one frame before load() resolves it.
     */
    val canRestructure: Boolean = true,
    /** Non-null while the delete-strategy dialog is open. */
    val pendingDelete: DeletePlan? = null,
    /**
     * The household's other live locations — the ONLY signal for whether the
     * dialog offers "move" (DeletePlan itself carries no canMove flag; see
     * DeletePlan.kt). Empty when the current selection is the household's only
     * location(s), so the dialog then correctly offers only delete-or-cancel.
     */
    val moveTargets: List<MoveTarget> = emptyList(),
    /** The batch just deleted, for the Undo snackbar. Cleared once consumed. */
    val lastBatchId: String? = null,
    /**
     * One-shot result of the last undoDelete() call — null while none is pending.
     * The screen turns this into the matching localized snackbar (delete_undone /
     * delete_undo_failed) and calls [StorageOverviewViewModel.consumeUndoResult].
     */
    val undoResult: UndoOutcome? = null,
)

// The method count is the required surface for this screen (edit mode, reorder,
// rename, strategy-gated delete, undo) — mirrors ShelvesViewModel's own shape,
// which needed the same baseline entry for the same reason (TooManyFunctions).
@HiltViewModel
class StorageOverviewViewModel
    @Inject
    constructor(
        private val repository: LocationRepository,
        private val hierarchyStore: HierarchyStore,
        private val restoreRepository: RestoreRepository,
    ) : ViewModel() {
        private var householdId: Long? = null

        // Re-entrancy guards (final review, stability audit) — mirrors
        // ShelvesViewModel's own moveJob/deleteJob: skip a new call while its
        // predecessor is still in flight rather than let a rapid double-tap
        // interleave two coroutines, where the SECOND call's success could be
        // clobbered by the FIRST call's (still in-flight) failure path restoring
        // a `preMove` snapshot captured before the second call ever ran.
        private var moveJob: Job? = null
        private var deleteJob: Job? = null

        private val _state = MutableStateFlow(StorageOverviewUiState())
        val state: StateFlow<StorageOverviewUiState> = _state.asStateFlow()

        fun load(householdId: Long) {
            val switched = this.householdId != householdId
            this.householdId = householdId
            _state.update { it.copy(canRestructure = canRestructureFor(householdId)) }
            if (!switched) {
                refreshSilent()
                return
            }
            val cached = repository.getCached(householdId)
            if (cached != null) {
                _state.update { it.copy(locations = orderLocations(cached)) }
                refreshSilent()
            } else {
                _state.update { it.copy(locations = emptyList()) }
                refresh()
            }
        }

        /**
         * Reads the household's role gate off [hierarchyStore] — already injected
         * here for the post-mutation `hierarchyStore.refresh()` calls below, so this
         * needs no extra repository dependency. Defaults true (never hides a pencil
         * the server would actually allow) when the store hasn't loaded this
         * household yet, e.g. a cold deep link straight into this screen.
         */
        private fun canRestructureFor(householdId: Long): Boolean =
            hierarchyStore.state.value.entries
                .find { it.id == householdId }
                ?.canRestructure ?: true

        fun onNewNameChange(value: String) =
            _state.update { it.copy(newName = value.take(MAX_LOCATION_NAME_LENGTH), error = null) }

        fun onTypeSelect(type: String) = _state.update { it.copy(newType = type) }

        fun refresh() {
            val id = householdId ?: return
            launchLoading(refreshing = true) {
                val locations = repository.list(id)
                _state.update { it.copy(locations = orderLocations(locations)) }
            }
        }

        fun create() {
            val id = householdId ?: return
            val name = _state.value.newName.trim()
            if (name.isEmpty()) return
            launchLoading {
                val created = repository.create(id, name, _state.value.newType)
                _state.update { it.copy(newName = "", locations = orderLocations(it.locations + created)) }
                hierarchyStore.refresh()
            }
        }

        fun enterEditMode() = _state.update { it.copy(editMode = true, selected = emptySet()) }

        fun exitEditMode() =
            _state.update {
                it.copy(
                    editMode = false,
                    selected = emptySet(),
                    pendingDelete = null,
                    moveTargets = emptyList(),
                )
            }

        fun toggleSelection(locationId: Long) =
            _state.update { state ->
                val exists = state.locations.any { it.id == locationId }
                if (!exists) {
                    state
                } else {
                    val updated =
                        if (locationId in state.selected) state.selected - locationId else state.selected + locationId
                    state.copy(selected = updated)
                }
            }

        fun rename(
            locationId: Long,
            name: String,
            type: String,
        ) {
            val id = householdId ?: return
            // Clamped here too (not just in the Compose text field), so this method
            // is safe to call from anywhere, not only the one wired-up sheet.
            val trimmed = name.trim().take(MAX_LOCATION_NAME_LENGTH)
            if (trimmed.isEmpty()) return
            launchLoading {
                val updated = repository.rename(id, locationId, trimmed, type)
                _state.update { s ->
                    s.copy(locations = orderLocations(s.locations.map { if (it.id == locationId) updated else it }))
                }
                hierarchyStore.refresh()
            }
        }

        fun moveUp(locationId: Long) = move(locationId, -1)

        fun moveDown(locationId: Long) = move(locationId, +1)

        private fun move(
            locationId: Long,
            delta: Int,
        ) {
            val id = householdId
            // Serialized: skip a new move while one is still in flight rather than
            // let a second tap's coroutine interleave with the first's (see the
            // field's own doc comment on moveJob). Merged into this same check
            // (rather than its own early return) to stay under ReturnCount's limit.
            if (id == null || moveJob?.isActive == true) return
            val current = _state.value.locations
            val index = current.indexOfFirst { it.id == locationId }
            val target = index + delta
            if (index < 0 || target !in current.indices) return

            val reordered = current.toMutableList().apply { add(target, removeAt(index)) }

            // Captured BEFORE the optimistic frame overwrites state (== `current`,
            // named separately for clarity at the point it's used below) — used
            // only as a last-resort fallback below if the server can't even be
            // reached to resync.
            val preMove = current

            // Optimistic: the row visibly moves on tap. The server call rewrites
            // every position in one transaction, so a failure snaps the whole list
            // back rather than leaving it half-sorted.
            _state.update { it.copy(locations = reordered) }

            moveJob =
                launchLoading {
                    // The COMPLETE ordered id list — a partial list produces duplicate
                    // positions server-side (LocationController::reorder rejects any
                    // list that isn't exactly every live location in this household).
                    val result = runCatching { repository.reorder(id, reordered.map { it.id }) }
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    if (result.isSuccess) {
                        // The server's response IS the full, current location list for
                        // this household (PATCH .../locations/reorder ends with `return
                        // $this->index(...)`, same shape as the shelves endpoint). Replace
                        // local state with it directly — do NOT merge/append anything
                        // else on top of it, or a location ends up listed twice and the
                        // list's LazyColumn (keyed by location.id) crashes on the
                        // duplicate key. (This is the exact bug Task 4 shipped for
                        // shelves, at the level above: there it was re-merging a local
                        // systemShelves copy onto a response that already contained it;
                        // here there is no system entry, so the fix is simply "replace,
                        // never merge.")
                        _state.update { it.copy(locations = orderLocations(result.getOrThrow())) }
                        hierarchyStore.refresh()
                    } else {
                        // The write never landed — the optimistic frame is a lie about
                        // server order. Re-sync from the server rather than blindly
                        // restoring the `preMove` snapshot: with concurrent moves now
                        // structurally impossible (the guard above), preMove can't
                        // have gone stale from another LOCAL mutation — but a resync
                        // still reflects whatever the server actually has, rather
                        // than a client-side guess frozen at this gesture's start, so
                        // it stays the more honest recovery. Fall back to preMove
                        // only if the resync itself can't reach the server either.
                        val resynced = runCatching { orderLocations(repository.list(id)) }.getOrNull()
                        _state.update { it.copy(locations = resynced ?: preMove) }
                        result.exceptionOrNull()?.let { throw it }
                    }
                }
        }

        /**
         * Open the strategy dialog for the current selection.
         *
         * Refreshes the location list from the server FIRST, then builds the
         * plan off that fresh copy — the same staleness concern as
         * ShelvesViewModel.requestDelete: a shelf added to a previously
         * shelf-less location via LocationDetailScreen's own add-shelf sheet
         * never updates this screen's cached `shelf_count` on its own.
         */
        fun requestDelete() {
            val id = householdId ?: return
            val selectedIds = _state.value.selected
            if (selectedIds.isEmpty()) return

            launchLoading {
                val locations = orderLocations(repository.list(id))
                _state.update { it.copy(locations = locations) }

                val selected = locations.filter { it.id in selectedIds }
                if (selected.isEmpty()) return@launchLoading

                val products = selected.sumOf { it.product_count }
                // The trap: what the SERVER counts as "has contents" for a
                // LOCATION is its SHELF count, not its product count — a
                // location with 3 completely empty shelves still needs a
                // strategy. contentCount must come from shelf_count, or this
                // delete goes out with no strategy and 422s. productCount still
                // feeds the summary line ("N products stored inside").
                val shelves = selected.sumOf { it.shelf_count }

                _state.update {
                    it.copy(
                        pendingDelete =
                            DeletePlan(
                                itemCount = selected.size,
                                productCount = products,
                                contentCount = shelves,
                            ),
                        // The household's other live locations: not themselves
                        // being deleted. This list is the ONLY signal for
                        // whether "move" is offered — DeletePlan carries no
                        // canMove flag (see DeletePlan.kt). Empty means the
                        // dialog hides the move option, so a move is never
                        // offered with nowhere to go.
                        moveTargets =
                            locations
                                .filter { l -> l.id !in selectedIds }
                                .map { l -> MoveTarget(id = l.id, name = l.name) },
                    )
                }
            }
        }

        fun confirmDelete(
            strategy: LocationDeleteStrategy?,
            targetId: Long?,
        ) {
            val id = householdId
            // Merged with the householdId-null check (rather than its own early
            // return) to stay under ReturnCount's limit.
            if (id == null || deleteJob?.isActive == true) return
            val state = _state.value
            // Same filter requestDelete() applies (and, by the time the dialog
            // is open, requestDelete() has already refreshed state.locations):
            // only ids that still exist in the live list, so a location that
            // vanished from under the user never gets a delete request for an
            // id that no longer exists.
            val ids = state.locations.filter { it.id in state.selected }.map { it.id }
            if (ids.isEmpty()) {
                // Every selected location vanished from the live list (someone else
                // deleted them first) between requestDelete()'s refresh and this
                // call. There is nothing left to delete — close the dialog and exit
                // edit mode the same way a successful/failed delete below does, or
                // the dialog is stuck open with a Confirm button that does nothing.
                closeDeleteFlow()
                return
            }

            // ONE batch id for the whole gesture. Deleting three locations is
            // three requests; if each minted its own id they would land in
            // three batches and Undo would restore only one of them.
            val batchId = UUID.randomUUID().toString()

            deleteJob =
                launchLoading {
                    val succeeded = mutableListOf<Long>()
                    var failure: Throwable? = null
                    for (locationId in ids) {
                        val result =
                            runCatching {
                                repository.deleteWithStrategy(
                                    id,
                                    locationId,
                                    LocationDeletion(batchId, strategy, targetId),
                                )
                            }
                        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        if (result.isSuccess) {
                            succeeded += locationId
                        } else {
                            // Stop at the first failure: whatever already landed
                            // server-side must still be reflected and made
                            // Undo-able, but nothing past the failure was
                            // attempted, so there is nothing more to reconcile.
                            failure = result.exceptionOrNull()
                            break
                        }
                    }

                    if (succeeded.isNotEmpty()) {
                        // A partial batch already changed the server's truth, so
                        // reconcile with a real refresh rather than a local filter
                        // — and surface Undo for whatever actually landed.
                        val fresh = orderLocations(repository.list(id))
                        _state.update { it.copy(locations = fresh, lastBatchId = batchId) }
                        hierarchyStore.refresh()
                    }

                    closeDeleteFlow()

                    failure?.let { throw it }
                }
        }

        /** Shared by confirmDelete()'s vanished-selection guard and its normal completion. */
        private fun closeDeleteFlow() =
            _state.update {
                it.copy(
                    editMode = false,
                    selected = emptySet(),
                    pendingDelete = null,
                    moveTargets = emptyList(),
                )
            }

        fun cancelDelete() = _state.update { it.copy(pendingDelete = null, moveTargets = emptyList()) }

        fun undoDelete() {
            val id = householdId ?: return
            val batchId = _state.value.lastBatchId
            // Merged with the batchId-null check (rather than its own early
            // return) to stay under ReturnCount's limit.
            if (batchId == null || deleteJob?.isActive == true) return
            deleteJob =
                launchLoading {
                    // A 409 here means the batch was already restored (another device,
                    // a double-tap) or permanently removed past the undo window — NOT
                    // a generic failure, so it does not rethrow into launchLoading's
                    // catch-all (state.error would otherwise show "Something went
                    // wrong." instead of the specific message the screen shows below).
                    val result = runCatching { restoreRepository.restore(id, batchId) }
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    if (result.isSuccess) {
                        _state.update { it.copy(lastBatchId = null, undoResult = UndoOutcome.SUCCESS) }
                        refresh()
                        hierarchyStore.refresh()
                    } else {
                        _state.update { it.copy(undoResult = UndoOutcome.FAILURE) }
                    }
                }
        }

        fun consumeLastBatch() = _state.update { it.copy(lastBatchId = null) }

        fun consumeUndoResult() = _state.update { it.copy(undoResult = null) }

        /** The one hierarchy ordering rule (Task 2): manual position, name tie-break. */
        private fun orderLocations(locations: List<LocationDto>): List<LocationDto> =
            orderByPosition(locations, { it.position }, { it.name })

        private fun refreshSilent() {
            val id = householdId ?: return
            viewModelScope.launch {
                runCatching { repository.list(id) }
                    .onSuccess { locations -> _state.update { it.copy(locations = orderLocations(locations)) } }
            }
        }

        private fun launchLoading(
            refreshing: Boolean = false,
            block: suspend () -> Unit,
        ): Job =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, refreshing = refreshing, error = null) }
                val result = runCatching { block() }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false, refreshing = false) },
                        onFailure = { error ->
                            state.copy(
                                loading = false,
                                refreshing = false,
                                error = error.toUserMessage("Something went wrong."),
                            )
                        },
                    )
                }
            }
    }
