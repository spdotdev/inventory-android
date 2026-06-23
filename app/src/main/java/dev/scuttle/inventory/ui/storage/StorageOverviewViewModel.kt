package dev.scuttle.inventory.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val STORAGE_TYPES = listOf("freezer", "fridge", "pantry", "other")

data class StorageOverviewUiState(
    val loading: Boolean = false,
    val locations: List<LocationDto> = emptyList(),
    val newName: String = "",
    val newType: String = "freezer",
    val error: String? = null,
)

@HiltViewModel
class StorageOverviewViewModel @Inject constructor(
    private val repository: LocationRepository,
) : ViewModel() {

    private var householdId: Long? = null

    private val _state = MutableStateFlow(StorageOverviewUiState())
    val state: StateFlow<StorageOverviewUiState> = _state.asStateFlow()

    /** Bind the screen's household and load its locations. Idempotent per id. */
    fun load(householdId: Long) {
        if (this.householdId == householdId && _state.value.locations.isNotEmpty()) return
        this.householdId = householdId
        refresh()
    }

    fun onNewNameChange(value: String) = _state.update { it.copy(newName = value, error = null) }

    fun onTypeSelect(type: String) = _state.update { it.copy(newType = type) }

    fun refresh() {
        val id = householdId ?: return
        launchLoading {
            val locations = repository.list(id)
            _state.update { it.copy(locations = locations) }
        }
    }

    fun create() {
        val id = householdId ?: return
        val name = _state.value.newName.trim()
        if (name.isEmpty()) return
        launchLoading {
            repository.create(id, name, _state.value.newType)
            _state.update { it.copy(newName = "") }
            _state.update { it.copy(locations = repository.list(id)) }
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
