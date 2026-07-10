package dev.scuttle.inventory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.household.HouseholdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinUiState(
    val loading: Boolean = false,
    val code: String = "",
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class JoinHouseholdViewModel
    @Inject
    constructor(
        private val repository: HouseholdRepository,
        private val hierarchyStore: HierarchyStore,
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
                // Refresh the drawer/home/dashboard so the joined household appears there
                // immediately, not just on the next manual pull-to-refresh (X4).
                if (result.isSuccess) hierarchyStore.refresh()
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false, code = "", success = true) },
                        onFailure = { e -> state.copy(loading = false, error = e.toUserMessage("Failed to join.")) },
                    )
                }
            }
        }
    }
