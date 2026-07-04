package dev.scuttle.inventory.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.scuttle.inventory.data.error.toUserMessage
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
    private val hierarchyStore: HierarchyStore,
) : ViewModel() {

    private var householdId: Long? = null

    private val _state = MutableStateFlow(StorageOverviewUiState())
    val state: StateFlow<StorageOverviewUiState> = _state.asStateFlow()

    fun load(householdId: Long) {
        val switched = this.householdId != householdId
        this.householdId = householdId
        if (!switched) {
            refreshSilent()
            return
        }
        val cached = repository.getCached(householdId)
        if (cached != null) {
            _state.update { it.copy(locations = cached) }
            refreshSilent()
        } else {
            _state.update { it.copy(locations = emptyList()) }
            refresh()
        }
    }

    fun onNewNameChange(value: String) = _state.update { it.copy(newName = value.take(50), error = null) }

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
            val created = repository.create(id, name, _state.value.newType)
            _state.update { it.copy(newName = "", locations = it.locations + created) }
            hierarchyStore.refresh()
        }
    }

    fun deleteLocation(locationId: Long) {
        val id = householdId ?: return
        launchLoading {
            repository.delete(id, locationId)
            _state.update { it.copy(locations = it.locations.filter { l -> l.id != locationId }) }
            hierarchyStore.refresh()
        }
    }

    private fun refreshSilent() {
        val id = householdId ?: return
        viewModelScope.launch {
            runCatching { repository.list(id) }
                .onSuccess { locations -> _state.update { it.copy(locations = locations) } }
        }
    }

    private fun launchLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { block() }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(loading = false) },
                    onFailure = { error -> state.copy(loading = false, error = error.toUserMessage("Something went wrong.")) },
                )
            }
        }
    }
}
