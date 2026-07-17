package dev.scuttle.inventory.ui.households

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.household.HouseholdRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdsUiState(
    val loading: Boolean = false,
    // Only a user-initiated refresh() flips this; create/leave use `loading` alone,
    // so the pull-to-refresh spinner doesn't fire on mutations.
    val refreshing: Boolean = false,
    val households: List<HouseholdDto> = emptyList(),
    val newName: String = "",
    val error: String? = null,
    // Gates whether tapping a household row navigates to HouseholdEditScreen —
    // mirrors StorageOverviewViewModel/ShelvesViewModel's editMode flag.
    val editMode: Boolean = false,
    // Set to the household's id once leave() has actually completed server-side for
    // it. HouseholdEditScreen waits for this (LaunchedEffect) instead of navigating
    // back the instant Leave is tapped — same pattern as ProductDetailViewModel.deleted
    // — so its own viewModelScope coroutine isn't cancelled mid-flight by the
    // navigation it would otherwise trigger.
    //
    // An id rather than a plain boolean flag is deliberate: this ViewModel is now a
    // SINGLE instance shared across every visit to HouseholdsScreen and
    // HouseholdEditScreen for the lifetime of the NavHost (MainActivity hoists it,
    // same as drawerViewModel — see InventoryNavHost), not a fresh instance per
    // household-edit visit. A plain boolean would stay stuck `true` after the FIRST
    // leave() ever completes in this session — LaunchedEffect(state.left) runs on
    // every fresh composition of HouseholdEditScreen regardless of whether the key's
    // VALUE actually changed, so a stale `true` would auto-navigate the user back out
    // of the very next household they open, before they could do anything. Comparing
    // against the CURRENT screen's own householdId instead makes the check correct
    // without needing an explicit one-shot "consume" reset call (and without adding a
    // function this class doesn't have room for under TooManyFunctions).
    val leftHouseholdId: Long? = null,
)

@HiltViewModel
class HouseholdsViewModel
    @Inject
    constructor(
        private val repository: HouseholdRepository,
        private val hierarchyStore: HierarchyStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HouseholdsUiState())
        val state: StateFlow<HouseholdsUiState> = _state.asStateFlow()

        init {
            val cached = repository.getCached()
            if (cached != null) {
                _state.update { it.copy(households = cached) }
                refreshSilent()
            } else {
                refresh()
            }
            // A remote promote/demote/remove (or a household's own theme edit made
            // by someone else) arrives over the socket as a `household.changed`
            // ping, which LiveUpdates turns into a silent [HierarchyStore.refresh]
            // only (data/realtime/LiveUpdates.kt). That store refresh's own
            // `buildFromNetwork()` already calls `repository.list()`, which updates
            // [HouseholdRepository]'s cache — but nothing previously pulled that
            // fresh data back into THIS VM's own `households`, which is what
            // HouseholdEditScreen/MembersScreen actually read `viewerRole`/
            // `canManageMembers`/`canRestructure` from. Without this, an affected
            // user's pencils/controls stayed wrong until a manual pull-to-refresh.
            //
            // Only applied once the store's own refresh has LANDED (`!loading`) and
            // only while this VM has no mutation of its own in flight (`!loading`
            // on `_state`) — a local create()/leave()/update() also triggers
            // `hierarchyStore.refresh()` as its last step (X4), and that nested
            // refresh's OWN `loading` transitions fire this same collector;
            // skipping while `_state.value.loading` is still true (it only flips
            // back to false once the local mutation's `launchLoading` block
            // returns, AFTER that nested refresh call) keeps a remote ping from
            // clobbering the local mutation's own repository.list()-derived result
            // with a same-or-stale read of the cache mid-mutation.
            viewModelScope.launch {
                hierarchyStore.state.collect { hierarchyState ->
                    if (hierarchyState.loading || _state.value.loading) return@collect
                    val fresh = repository.getCached() ?: return@collect
                    _state.update { it.copy(households = fresh) }
                }
            }
        }

        fun onNewNameChange(value: String) = _state.update { it.copy(newName = value.take(50), error = null) }

        fun refresh() =
            launchLoading(refreshing = true) {
                _state.update { it.copy(households = repository.list()) }
            }

        private fun refreshSilent() {
            viewModelScope.launch {
                runCatching { repository.list() }
                    .onSuccess { households -> _state.update { it.copy(households = households) } }
            }
        }

        fun create() {
            val name = _state.value.newName.trim()
            if (name.isEmpty()) return
            launchLoading {
                repository.create(name)
                _state.update { it.copy(newName = "") }
                _state.update { it.copy(households = repository.list()) }
                // Keep the drawer/home/dashboard (driven by HierarchyStore) in sync — a
                // new household would otherwise stay invisible there until a manual
                // pull-to-refresh or relaunch (X4).
                hierarchyStore.refresh()
            }
        }

        fun leave(householdId: Long) =
            launchLoading {
                repository.leave(householdId)
                _state.update { it.copy(households = repository.list(), leftHouseholdId = householdId) }
                hierarchyStore.refresh()
            }

        fun enterEditMode() = _state.update { it.copy(editMode = true) }

        fun exitEditMode() = _state.update { it.copy(editMode = false) }

        /**
         * Rename and/or re-theme a household. A thin pass-through to
         * [HouseholdRepository.update] — it does NOT infer "leave this alone" from
         * a null argument the way the server infers it from an ABSENT key. Callers
         * must supply every field they don't want touched with its CURRENT value:
         * `color`/`icon` have no default on the wire (UpdateHouseholdRequest), so
         * they are always sent, and an explicit null there clears the theme back
         * to the derived default. `name` omits itself when null (its default), so
         * `name = null` is the correct way to leave the name untouched — see
         * [updateTheme], which relies on exactly that.
         */
        fun update(
            householdId: Long,
            name: String?,
            color: String?,
            icon: String?,
        ) = launchLoading {
            val updated = repository.update(householdId, name = name, color = color, icon = icon)
            _state.update { s ->
                s.copy(households = s.households.map { if (it.id == updated.id) updated else it })
            }
            // Drawer avatars read HierarchyStore, not this VM's list (X4).
            hierarchyStore.refresh()
        }

        /** Persist the chosen theme keys (null = back to the derived default); the name is never touched. */
        fun updateTheme(
            householdId: Long,
            color: String?,
            icon: String?,
        ) = update(householdId, name = null, color = color, icon = icon)

        private fun launchLoading(
            refreshing: Boolean = false,
            block: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, refreshing = refreshing, error = null) }
                val result = runCatching { block() }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false, refreshing = false) },
                        onFailure = { e ->
                            state.copy(
                                loading = false,
                                refreshing = false,
                                error = e.toUserMessage("Something went wrong."),
                            )
                        },
                    )
                }
            }
        }
    }
