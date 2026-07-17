package dev.scuttle.inventory.ui.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.hierarchy.ShelfDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.ShelfDeletion
import dev.scuttle.inventory.data.settings.ShelfViewStore
import dev.scuttle.inventory.data.shelf.ShelfRepository
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

/** Server-side shelf name column limit — clamped here (rename) and in onNewNameChange (create). */
private const val MAX_SHELF_NAME_LENGTH = 50

data class ShelvesUiState(
    val loading: Boolean = false,
    // Only a user-initiated refresh() flips this; create/delete use `loading` alone,
    // so the pull-to-refresh spinner doesn't fire on mutations.
    val refreshing: Boolean = false,
    val shelves: List<ShelfDto> = emptyList(),
    val newName: String = "",
    val error: String? = null,
    val editMode: Boolean = false,
    /**
     * Mirrors this household's HouseholdDto.can_restructure (via
     * [dev.scuttle.inventory.data.HouseholdWithLocations.canRestructure]) — gates
     * the top-bar edit pencil so a Member never opens rename/reorder/delete
     * affordances every mutating ShelfController route already 403s for them
     * server-side. Defaults true so the pencil isn't hidden for the one frame
     * before load() resolves it.
     */
    val canRestructure: Boolean = true,
    val selected: Set<Long> = emptySet(),
    val listView: Boolean = false,
    /** Non-null while the delete-strategy dialog is open. */
    val pendingDelete: DeletePlan? = null,
    /**
     * The live, non-system shelves the current selection's products could move to
     * — computed fresh by [ShelvesViewModel.requestDelete]. This is the ONLY signal
     * for whether the dialog offers "move" (see DeletePlan.kt: DeletePlan itself
     * carries no canMove flag). Empty means no move option is shown.
     */
    val moveTargets: List<MoveTarget> = emptyList(),
    /** The batch just deleted, for the Undo snackbar. Cleared once consumed. */
    val lastBatchId: String? = null,
    /**
     * One-shot result of the last undoDelete() call — null while none is pending.
     * The screen turns this into the matching localized snackbar (delete_undone /
     * delete_undo_failed) and calls [ShelvesViewModel.consumeUndoResult].
     */
    val undoResult: UndoOutcome? = null,
)

// The method count is the required surface for this screen (edit mode, reorder,
// rename, strategy-gated delete, undo, view toggle) — Task 5 mirrors this same
// shape for locations, so splitting it up would fight the spec rather than the
// class's actual cohesion (every method operates on the one ShelvesUiState).
// (TooManyFunctions is baselined in detekt-baseline.xml, same as AuthViewModel.)
@HiltViewModel
class ShelvesViewModel
    @Inject
    constructor(
        private val repository: ShelfRepository,
        private val restoreRepository: RestoreRepository,
        private val shelfViewStore: ShelfViewStore,
        private val hierarchyStore: HierarchyStore,
    ) : ViewModel() {
        private var householdId: Long? = null
        private var locationId: Long? = null

        // Re-entrancy guards (final review, stability audit): a rapid double-tap
        // used to launch a second coroutine on top of the first, and that second
        // move's success could then be clobbered when the FIRST call's failure
        // path (still in flight) restored a `preMove` snapshot captured before
        // the second call ever ran. Serializing per-operation — skip a new call
        // while its predecessor is still active — makes that interleaving
        // structurally impossible; a dropped rapid second tap is preferable to a
        // corrupted order (up/down buttons, not a text field, so nothing typed
        // is ever lost). moveJob and deleteJob are deliberately separate: a
        // reorder in flight has no reason to block a delete gesture or vice versa.
        private var moveJob: Job? = null
        private var deleteJob: Job? = null

        private val _state = MutableStateFlow(ShelvesUiState(listView = shelfViewStore.isListView()))
        val state: StateFlow<ShelvesUiState> = _state.asStateFlow()

        /** Remembered so exiting edit mode restores the view the user actually chose. */
        private var viewBeforeEdit: Boolean = false

        fun load(
            householdId: Long,
            locationId: Long,
        ) {
            val switched = this.householdId != householdId || this.locationId != locationId
            this.householdId = householdId
            this.locationId = locationId
            _state.update { it.copy(canRestructure = canRestructureFor(householdId)) }
            if (!switched) {
                refreshSilent()
                return
            }
            val cached = repository.getCached(householdId, locationId)
            if (cached != null) {
                _state.update { it.copy(shelves = orderShelves(cached)) }
                refreshSilent()
            } else {
                _state.update { it.copy(shelves = emptyList()) }
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
            _state.update { it.copy(newName = value.take(MAX_SHELF_NAME_LENGTH), error = null) }

        fun refresh() {
            val h = householdId ?: return
            val l = locationId ?: return
            launchLoading(refreshing = true) {
                val shelves = repository.list(h, l)
                _state.update { it.copy(shelves = orderShelves(shelves)) }
            }
        }

        fun create() {
            val h = householdId ?: return
            val l = locationId ?: return
            val name = _state.value.newName.trim()
            if (name.isEmpty()) return
            launchLoading {
                val created = repository.create(h, l, name)
                _state.update { it.copy(newName = "", shelves = orderShelves(it.shelves + created)) }
            }
        }

        fun toggleListView() {
            val next = !_state.value.listView
            shelfViewStore.setListView(next)
            _state.update { it.copy(listView = next) }
        }

        fun enterEditMode() {
            // Tabs cannot host reorder buttons or an inline rename target, so edit
            // mode always runs in the list view — and restores the user's choice
            // on the way out.
            viewBeforeEdit = _state.value.listView
            _state.update { it.copy(editMode = true, listView = true, selected = emptySet()) }
        }

        fun exitEditMode() =
            _state.update {
                it.copy(
                    editMode = false,
                    listView = viewBeforeEdit,
                    selected = emptySet(),
                    pendingDelete = null,
                    moveTargets = emptyList(),
                )
            }

        fun toggleSelection(shelfId: Long) =
            _state.update { state ->
                // The Unsorted shelf holds the products the user chose to KEEP.
                // A stray checkbox tap must not be able to destroy it.
                val shelf = state.shelves.firstOrNull { it.id == shelfId }
                if (shelf == null || shelf.is_system) {
                    state
                } else {
                    val updated =
                        if (shelfId in state.selected) state.selected - shelfId else state.selected + shelfId
                    state.copy(selected = updated)
                }
            }

        fun rename(
            shelfId: Long,
            name: String,
        ) {
            val h = householdId
            val l = locationId
            if (h == null || l == null) return
            val shelf = _state.value.shelves.firstOrNull { it.id == shelfId }
            // Clamped here too (not just in the Compose text field), so this method
            // is safe to call from anywhere, not only the one wired-up dialog.
            val trimmed = name.trim().take(MAX_SHELF_NAME_LENGTH)
            // The Unsorted shelf's name is server-owned; the client only localises
            // its displayed label, so it can never be renamed from here.
            if (shelf == null || shelf.is_system || trimmed.isEmpty()) return
            launchLoading {
                val updated = repository.rename(h, l, shelfId, trimmed)
                _state.update { s ->
                    s.copy(shelves = orderShelves(s.shelves.map { if (it.id == shelfId) updated else it }))
                }
                hierarchyStore.refresh()
            }
        }

        fun moveUp(shelfId: Long) = move(shelfId, -1)

        fun moveDown(shelfId: Long) = move(shelfId, +1)

        private fun move(
            shelfId: Long,
            delta: Int,
        ) {
            val h = householdId
            val l = locationId
            // Serialized: skip a new move while one is still in flight rather than
            // let a second tap's coroutine interleave with the first's (see the
            // field's own doc comment on moveJob). Merged into this same check
            // (rather than its own early return) to stay under ReturnCount's limit.
            if (h == null || l == null || moveJob?.isActive == true) return
            // The system shelf is never part of the manual drag order and never
            // travels in the reorder payload — see reorder() below.
            val current = _state.value.shelves.filterNot { it.is_system }
            val systemShelves = _state.value.shelves.filter { it.is_system }
            val index = current.indexOfFirst { it.id == shelfId }
            val target = index + delta
            if (index < 0 || target !in current.indices) return

            val reordered = current.toMutableList().apply { add(target, removeAt(index)) }

            // Captured BEFORE the optimistic frame overwrites state — used only as
            // a last-resort fallback below if the server can't even be reached to
            // resync.
            val preMove = _state.value.shelves

            // Optimistic: the row visibly moves on tap. The server call rewrites
            // every position in one transaction, so a failure snaps the whole list
            // back rather than leaving it half-sorted.
            _state.update { it.copy(shelves = reordered + systemShelves) }

            moveJob =
                launchLoading {
                    // The COMPLETE ordered id list, system shelf excluded — a
                    // partial list produces duplicate positions server-side, and
                    // the server does not want the system shelf in the payload at
                    // all.
                    val result = runCatching { repository.reorder(h, l, reordered.map { it.id }) }
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    if (result.isSuccess) {
                        // The server's response is the FULL shelf list for this
                        // location, is_system included (PATCH .../shelves/reorder
                        // ends with `return $this->index(...)`). Appending our own
                        // local systemShelves on top of that would duplicate the
                        // system shelf's id, and the list view's LazyColumn keys
                        // itemsIndexed by shelf.id — a duplicate key throws
                        // IllegalArgumentException on every reorder. Strip any
                        // system shelf(s) the server sent back before adding ours.
                        val server = result.getOrThrow()
                        _state.update {
                            it.copy(shelves = orderShelves(server.filterNot { s -> s.is_system } + systemShelves))
                        }
                        hierarchyStore.refresh()
                    } else {
                        // The write never landed — the optimistic frame is a lie
                        // about server order. Re-sync from the server rather than
                        // blindly restoring the `preMove` snapshot: with concurrent
                        // moves now structurally impossible (the guard above),
                        // preMove can't have gone stale from another LOCAL mutation
                        // — but a resync still reflects whatever the server
                        // actually has, rather than a client-side guess frozen at
                        // this gesture's start, so it stays the more honest
                        // recovery. Fall back to preMove only if the resync itself
                        // can't reach the server either.
                        val resynced = runCatching { orderShelves(repository.list(h, l)) }.getOrNull()
                        _state.update { it.copy(shelves = resynced ?: preMove) }
                        result.exceptionOrNull()?.let { throw it }
                    }
                }
        }

        /**
         * Open the strategy dialog for the current selection.
         *
         * Refreshes the shelf list from the server FIRST, then builds the plan off
         * that fresh copy. ShelfDto.product_count's own doc comment warns why this
         * is required: add-product / barcode-scan / product-delete all mutate a
         * shelf's product count without this screen ever hearing about it, so the
         * cached `state.shelves[].product_count` this dialog used to read from can
         * be stale. Building `needsStrategy` off a stale zero would silently drop
         * the strategy prompt and send a strategy-less delete for a shelf that
         * still holds products — a guaranteed 422.
         */
        fun requestDelete() {
            val h = householdId
            val l = locationId
            if (h == null || l == null) return
            val selectedIds = _state.value.selected
            if (selectedIds.isEmpty()) return

            launchLoading {
                val shelves = orderShelves(repository.list(h, l))
                _state.update { it.copy(shelves = shelves) }

                val selected = shelves.filter { it.id in selectedIds }
                if (selected.isEmpty()) return@launchLoading

                // For a SHELF, what the server counts as "has contents" IS its product
                // count — so contentCount and productCount coincide here. They do NOT
                // coincide for a location (Task 5): there, contentCount is shelf_count.
                val products = selected.sumOf { it.product_count }

                _state.update {
                    it.copy(
                        pendingDelete =
                            DeletePlan(
                                itemCount = selected.size,
                                productCount = products,
                                contentCount = products,
                            ),
                        // The shelves the products could move TO: live, non-system, and
                        // not themselves being deleted. This list is the ONLY signal for
                        // whether "move" is offered — DeletePlan carries no canMove flag
                        // (see DeletePlan.kt). Empty list => the dialog hides the move
                        // option, so a move is never offered with nowhere to go.
                        moveTargets =
                            shelves
                                .filter { s -> s.id !in selectedIds && !s.is_system }
                                .map { s -> MoveTarget(id = s.id, name = s.name) },
                    )
                }
            }
        }

        fun confirmDelete(
            strategy: ShelfDeleteStrategy?,
            targetId: Long?,
        ) {
            val h = householdId
            val l = locationId
            if (h == null || l == null || deleteJob?.isActive == true) return
            val state = _state.value
            // Same filter requestDelete() applies (and, by the time the dialog is
            // open, requestDelete() has already refreshed state.shelves): only ids
            // that still exist in the live list, so a shelf that vanished from
            // under the user — another device, a refresh while the dialog was open
            // — never gets a delete request for an id that no longer exists.
            val ids = state.shelves.filter { it.id in state.selected }.map { it.id }
            if (ids.isEmpty()) return

            // ONE batch id for the whole gesture. Deleting three shelves is three
            // requests; if each minted its own id they would land in three batches
            // and Undo would restore only one of them.
            val batchId = UUID.randomUUID().toString()

            deleteJob =
                launchLoading {
                    val succeeded = mutableListOf<Long>()
                    var failure: Throwable? = null
                    for (id in ids) {
                        val result =
                            runCatching {
                                repository.deleteWithStrategy(h, l, id, ShelfDeletion(batchId, strategy, targetId))
                            }
                        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        if (result.isSuccess) {
                            succeeded += id
                        } else {
                            // Stop at the first failure: whatever already landed
                            // server-side must still be reflected and made Undo-able,
                            // but nothing past the failure was attempted, so there is
                            // nothing more to reconcile for those ids.
                            failure = result.exceptionOrNull()
                            break
                        }
                    }

                    if (succeeded.isNotEmpty()) {
                        // A partial batch already changed the server's truth (and a
                        // MOVE/UNSORT strategy can shift another shelf's product_count
                        // too), so reconcile with a real refresh rather than a local
                        // filter — and surface Undo for whatever actually landed.
                        val fresh = orderShelves(repository.list(h, l))
                        _state.update { it.copy(shelves = fresh, lastBatchId = batchId) }
                        hierarchyStore.refresh()
                    }

                    // Cancel and Delete must leave the screen in the same shape: exit
                    // edit mode and restore the pre-edit view either way.
                    _state.update {
                        it.copy(
                            editMode = false,
                            listView = viewBeforeEdit,
                            selected = emptySet(),
                            pendingDelete = null,
                            moveTargets = emptyList(),
                        )
                    }

                    failure?.let { throw it }
                }
        }

        fun cancelDelete() = _state.update { it.copy(pendingDelete = null, moveTargets = emptyList()) }

        fun undoDelete() {
            val h = householdId ?: return
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
                    val result = runCatching { restoreRepository.restore(h, batchId) }
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

        /**
         * The one hierarchy ordering rule (orderByPosition) plus the system-shelf
         * exception: Unsorted always renders last, regardless of its own position,
         * because it is never part of the manual reorder the user controls.
         */
        private fun orderShelves(shelves: List<ShelfDto>): List<ShelfDto> {
            val (system, normal) = shelves.partition { it.is_system }
            return orderByPosition(normal, { it.position }, { it.name }) +
                orderByPosition(system, { it.position }, { it.name })
        }

        private fun refreshSilent() {
            val h = householdId ?: return
            val l = locationId ?: return
            viewModelScope.launch {
                runCatching { repository.list(h, l) }
                    .onSuccess { shelves -> _state.update { it.copy(shelves = orderShelves(shelves)) } }
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
