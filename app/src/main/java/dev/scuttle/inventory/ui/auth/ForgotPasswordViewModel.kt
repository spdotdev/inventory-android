package dev.scuttle.inventory.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.error.toUserMessageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val loading: Boolean = false,
    val sent: Boolean = false,
    val errorRes: Int? = null,
)

@HiltViewModel
class ForgotPasswordViewModel
    @Inject
    constructor(
        private val repository: AuthRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ForgotPasswordUiState())
        val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

        fun onEmailChange(value: String) = _state.update { it.copy(email = value, errorRes = null) }

        /**
         * GAP5-M6: "Wrong email? Try again" from the sent-state — returns to the
         * input state without a dead end. Keeps the typed email in place (it's
         * still editable, not cleared) since the point is fixing a typo, not
         * starting over from a blank field.
         */
        fun resetToInput() = _state.update { it.copy(sent = false, errorRes = null) }

        fun submit() {
            val email = _state.value.email.trim()
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                val result = runCatching { repository.forgotPassword(email) }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false, sent = true) },
                        onFailure = { e ->
                            state.copy(loading = false, errorRes = e.toUserMessageRes(R.string.error_generic))
                        },
                    )
                }
            }
        }
    }
