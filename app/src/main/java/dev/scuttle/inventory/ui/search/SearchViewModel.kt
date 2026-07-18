package dev.scuttle.inventory.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.error.toUserMessageRes
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.common.sortedByOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val loading: Boolean = false,
    val query: String = "",
    val results: List<SearchResultDto> = emptyList(),
    // H3: an R.string.* id, not a raw literal — resolved via stringResource() in the composable.
    val errorRes: Int? = null,
    val sort: SortOrder = SortOrder.NAME_ASC,
    // True only when [query] arrived via [SearchViewModel.searchFor] (the
    // scan-to-lookup flow), never from typed input (GAP-5 H6) — gates the
    // "Add a product with this barcode" CTA on a zero-result search so it
    // never appears for an ordinary typed miss where "this barcode" wouldn't
    // even make sense.
    val scanOriginated: Boolean = false,
) {
    /** Results after the user's local sort — the server query is the filter here. */
    val sortedResults: List<SearchResultDto>
        get() = results.sortedByOrder(sort, name = { it.name }, quantity = { it.quantity })
}

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val repository: SearchRepository,
        private val hierarchyStore: HierarchyStore,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private var householdId: Long? = null
        private var searchJob: Job? = null

        // GAP-8 (process death): the query and its scan-origin flag survive the
        // process being killed in the background — GAP5-H7 covered back-nav
        // (ViewModel survives), but a real process death rebuilt an empty
        // screen. Results are NOT persisted: they re-fetch from the restored
        // query on the first setHousehold() call (server-authoritative).
        private val _state =
            MutableStateFlow(
                SearchUiState(
                    query = savedState[KEY_QUERY] ?: "",
                    scanOriginated = savedState[KEY_SCAN_ORIGINATED] ?: false,
                ),
            )
        val state: StateFlow<SearchUiState> = _state.asStateFlow()

        init {
            // GAP6-M1: a remote household.changed ping (someone else adds/moves/deletes
            // a product) arrives via LiveUpdates as hierarchyStore.refresh() only —
            // nothing previously re-ran an already-active search, so a visible result
            // list went stale until a manual pull-to-refresh. Mirrors
            // HouseholdsViewModel's observeHierarchyStore() (bc0ea63): only react once
            // the store's own refresh has LANDED (!loading), and only while this VM has
            // no mutation of its own in flight (!loading on _state) so a remote ping
            // never clobbers/duplicates an in-flight local search. Silently re-runs the
            // CURRENT query only when one is active — an idle search box has nothing to
            // refresh.
            viewModelScope.launch {
                hierarchyStore.state.collect { hierarchyState ->
                    if (hierarchyState.loading || _state.value.loading) return@collect
                    if (_state.value.query.isNotBlank()) search()
                }
            }
        }

        /**
         * Only resets [state] when [householdId] actually CHANGED, not on every call
         * (GAP-5 H7): SearchScreen calls this from a `LaunchedEffect(householdId)` that
         * re-runs whenever the composable re-enters composition with a live household
         * id, which includes returning from ProductDetail via back-nav — the SAME
         * back-stack entry (and this same ViewModel instance, since it's scoped to that
         * entry the way `hiltViewModel()`'s default resolves — see HouseholdsViewModel's
         * hoisting doc comment in MainActivity for the general pattern) survives that
         * round trip. Wiping state unconditionally here would silently discard the
         * user's typed query and results on every return trip even though nothing
         * about the search actually needs to restart.
         */
        fun setHousehold(householdId: Long) {
            if (this.householdId == householdId) {
                if (_state.value.query.isNotBlank()) search()
                return
            }
            val firstBind = this.householdId == null
            this.householdId = householdId
            if (firstBind) {
                // First bind after (re)construction — includes the
                // process-death restore path, where the query just came back
                // from SavedStateHandle. Re-run it instead of resetting, or
                // the restore would be wiped by the very first bind.
                if (_state.value.query.isNotBlank()) search()
                return
            }
            savedState[KEY_QUERY] = ""
            savedState[KEY_SCAN_ORIGINATED] = false
            _state.update { SearchUiState() }
        }

        fun onQueryChange(value: String) {
            savedState[KEY_QUERY] = value
            savedState[KEY_SCAN_ORIGINATED] = false
            _state.update { it.copy(query = value, errorRes = null, scanOriginated = false) }
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    delay(300)
                    doSearch()
                }
        }

        fun search() {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { doSearch() }
        }

        /**
         * Applies [query] as the current search text and runs it immediately —
         * for callers (the scan-to-lookup flow) that already have the exact term
         * to search, not keystrokes to debounce like [onQueryChange]'s 300ms wait.
         */
        fun searchFor(query: String) {
            searchJob?.cancel()
            savedState[KEY_QUERY] = query
            savedState[KEY_SCAN_ORIGINATED] = true
            _state.update { it.copy(query = query, errorRes = null, scanOriginated = true) }
            searchJob = viewModelScope.launch { doSearch() }
        }

        fun setSort(order: SortOrder) = _state.update { it.copy(sort = order) }

        private suspend fun doSearch() {
            val h = householdId ?: return
            val q = _state.value.query.trim()
            if (q.isEmpty()) {
                _state.update { it.copy(results = emptyList(), loading = false) }
                return
            }
            _state.update { it.copy(loading = true, errorRes = null) }
            val result = runCatching { repository.search(h, q) }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            _state.update { state ->
                result.fold(
                    onSuccess = { results -> state.copy(loading = false, results = results) },
                    onFailure = { error ->
                        state.copy(loading = false, errorRes = error.toUserMessageRes(R.string.error_search_failed))
                    },
                )
            }
        }
    }

private const val KEY_QUERY = "search_query"
private const val KEY_SCAN_ORIGINATED = "search_scan_originated"
