package dev.scuttle.inventory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.household.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.scuttle.inventory.data.error.toUserMessage
import javax.inject.Inject

data class JoinUiState(
    val loading: Boolean = false,
    val code: String = "",
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class JoinHouseholdViewModel @Inject constructor(
    private val repository: HouseholdRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) = _state.update { it.copy(code = value, error = null, success = false) }

    fun join() {
        val code = _state.value.code.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, success = false) }
            val result = runCatching { repository.join(code) }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(loading = false, code = "", success = true) },
                    onFailure = { e -> state.copy(loading = false, error = e.toUserMessage("Failed to join.")) },
                )
            }
        }
    }
}
