package dev.scuttle.inventory.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.search.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
) : ViewModel() {

    private var householdId: Long? = null

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun setHousehold(householdId: Long) {
        this.householdId = householdId
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, error = null) }
        search()
    }

    fun search() {
        val h = householdId ?: return
        val q = _state.value.query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { repository.search(h, q) }
            _state.update { state ->
                result.fold(
                    onSuccess = { results -> state.copy(loading = false, results = results) },
                    onFailure = { error -> state.copy(loading = false, error = error.message ?: "Search failed.") },
                )
            }
        }
    }
}
