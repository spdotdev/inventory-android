package dev.scuttle.inventory.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.error.toUserMessage
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
    val error: String? = null,
    val sort: SortOrder = SortOrder.NAME_ASC,
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

        fun setHousehold(householdId: Long) {
            if (this.householdId == householdId) {
                if (_state.value.query.isNotBlank()) search()
                return
            }
            this.householdId = householdId
            _state.update { SearchUiState() }
        }

        fun onQueryChange(value: String) {
            _state.update { it.copy(query = value, error = null) }
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

        fun setSort(order: SortOrder) = _state.update { it.copy(sort = order) }

        private suspend fun doSearch() {
            val h = householdId ?: return
            val q = _state.value.query.trim()
            if (q.isEmpty()) {
                _state.update { it.copy(results = emptyList(), loading = false) }
                return
            }
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { repository.search(h, q) }
            _state.update { state ->
                result.fold(
                    onSuccess = { results -> state.copy(loading = false, results = results) },
                    onFailure = { error -> state.copy(loading = false, error = error.toUserMessage("Search failed.")) },
                )
            }
        }
    }
