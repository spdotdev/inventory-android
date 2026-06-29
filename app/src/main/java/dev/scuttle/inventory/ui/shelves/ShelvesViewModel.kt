package dev.scuttle.inventory.ui.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShelvesUiState(
    val loading: Boolean = false,
    val shelves: List<ShelfDto> = emptyList(),
    val newName: String = "",
    val error: String? = null,
    val deleteMode: Boolean = false,
    val selectedShelves: Set<Long> = emptySet(),
)

@HiltViewModel
class ShelvesViewModel @Inject constructor(
    private val repository: ShelfRepository,
) : ViewModel() {

    private var householdId: Long? = null
    private var locationId: Long? = null

    private val _state = MutableStateFlow(ShelvesUiState())
    val state: StateFlow<ShelvesUiState> = _state.asStateFlow()

    fun load(householdId: Long, locationId: Long) {
        if (this.householdId == householdId && this.locationId == locationId && _state.value.shelves.isNotEmpty()) return
        this.householdId = householdId
        this.locationId = locationId
        refresh()
    }

    fun onNewNameChange(value: String) = _state.update { it.copy(newName = value.take(50), error = null) }

    fun refresh() {
        val h = householdId ?: return
        val l = locationId ?: return
        launchLoading {
            val shelves = repository.list(h, l)
            _state.update { it.copy(shelves = shelves) }
        }
    }

    fun create() {
        val h = householdId ?: return
        val l = locationId ?: return
        val name = _state.value.newName.trim()
        if (name.isEmpty()) return
        launchLoading {
            repository.create(h, l, name)
            _state.update { it.copy(newName = "") }
            _state.update { it.copy(shelves = repository.list(h, l)) }
        }
    }

    fun enterDeleteMode() = _state.update { it.copy(deleteMode = true, selectedShelves = emptySet()) }

    fun exitDeleteMode() = _state.update { it.copy(deleteMode = false, selectedShelves = emptySet()) }

    fun toggleShelfSelection(shelfId: Long) = _state.update { state ->
        val updated = if (shelfId in state.selectedShelves) state.selectedShelves - shelfId
                      else state.selectedShelves + shelfId
        state.copy(selectedShelves = updated)
    }

    fun deleteSelected() {
        val h = householdId ?: return
        val l = locationId ?: return
        val ids = _state.value.selectedShelves.toList()
        if (ids.isEmpty()) return
        launchLoading {
            ids.forEach { id -> repository.delete(h, l, id) }
            _state.update { it.copy(deleteMode = false, selectedShelves = emptySet(), shelves = repository.list(h, l)) }
        }
    }

    private fun launchLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { block() }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(loading = false) },
                    onFailure = { error -> state.copy(loading = false, error = error.message ?: "Something went wrong.") },
                )
            }
        }
    }
}
