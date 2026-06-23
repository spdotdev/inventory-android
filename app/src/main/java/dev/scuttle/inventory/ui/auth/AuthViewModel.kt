package dev.scuttle.inventory.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(authenticated = repository.isAuthenticated()))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun toggleMode() = _state.update {
        it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN, error = null)
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repository.logout() }
            _state.value = AuthUiState()
        }
    }

    fun submit() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching {
                when (current.mode) {
                    AuthMode.LOGIN -> repository.login(current.email.trim(), current.password)
                    AuthMode.REGISTER -> repository.register(current.name.trim(), current.email.trim(), current.password)
                }
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(loading = false, authenticated = true) },
                    onFailure = { error -> state.copy(loading = false, error = error.message ?: "Authentication failed.") },
                )
            }
        }
    }
}
