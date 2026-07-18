package dev.scuttle.inventory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.error.toUserMessageRes
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
    // H3: an R.string.* id, not a raw literal — resolved via stringResource() in the composable.
    val errorRes: Int? = null,
    val success: Boolean = false,
    // The role the caller was granted on the just-joined household (M2), so the
    // success message can hint at the role model for a brand-new Member — who
    // joined via code with zero other explanation of what roles mean. Null once
    // [success] resets (see onCodeChange/join's initial update).
    val joinedRole: String? = null,
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

        fun onCodeChange(value: String) =
            _state.update { it.copy(code = value, errorRes = null, success = false, joinedRole = null) }

        /** A scanned invite QR carries the invite *link*, so show the user the code inside it (#30). */
        fun onCodeScanned(contents: String) = onCodeChange(parseJoinCode(contents))

        fun join() {
            // Also parse here so a pasted link works, not just a scanned one.
            val code = parseJoinCode(_state.value.code)
            if (code.isEmpty()) return
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null, success = false, joinedRole = null) }
                val result = runCatching { repository.join(code) }
                // Refresh the drawer/home/dashboard so the joined household appears there
                // immediately, not just on the next manual pull-to-refresh (X4).
                if (result.isSuccess) hierarchyStore.refresh()
                _state.update { state ->
                    result.fold(
                        onSuccess = { household ->
                            state.copy(loading = false, code = "", success = true, joinedRole = household.role)
                        },
                        onFailure = { e ->
                            state.copy(loading = false, errorRes = e.toUserMessageRes(R.string.error_failed_to_join))
                        },
                    )
                }
            }
        }
    }
