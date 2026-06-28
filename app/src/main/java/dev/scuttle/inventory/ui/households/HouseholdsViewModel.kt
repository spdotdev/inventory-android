package dev.scuttle.inventory.ui.households

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdsUiState(
    val loading: Boolean = false,
    val households: List<HouseholdDto> = emptyList(),
    val newName: String = "",
    val joinCode: String = "",
    val error: String? = null,
)

@HiltViewModel
class HouseholdsViewModel @Inject constructor(
    private val repository: HouseholdRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HouseholdsUiState())
    val state: StateFlow<HouseholdsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onNewNameChange(value: String) = _state.update { it.copy(newName = value, error = null) }

    fun onJoinCodeChange(value: String) = _state.update { it.copy(joinCode = value, error = null) }

    fun refresh() = launchLoading {
        val households = repository.list()
        _state.update { it.copy(households = households) }
    }

    fun create() {
        val name = _state.value.newName.trim()
        if (name.isEmpty()) return
        launchLoading {
            repository.create(name)
            _state.update { it.copy(newName = "") }
            _state.update { it.copy(households = repository.list()) }
        }
    }

    fun join() {
        val code = _state.value.joinCode.trim()
        if (code.isEmpty()) return
        launchLoading {
            repository.join(code)
            _state.update { it.copy(joinCode = "") }
            _state.update { it.copy(households = repository.list()) }
        }
    }

    fun leave(householdId: Long) = launchLoading {
        repository.leave(householdId)
        _state.update { it.copy(households = repository.list()) }
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
