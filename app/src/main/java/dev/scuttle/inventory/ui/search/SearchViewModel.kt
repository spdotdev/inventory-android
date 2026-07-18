package dev.scuttle.inventory.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.error.toUserMessageRes
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.common.sortedByOrder
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
    ) : ViewModel() {
        private var householdId: Long? = null
        private var searchJob: Job? = null

        private val _state = MutableStateFlow(SearchUiState())
        val state: StateFlow<SearchUiState> = _state.asStateFlow()

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
            this.householdId = householdId
            _state.update { SearchUiState() }
        }

        fun onQueryChange(value: String) {
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
