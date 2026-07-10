package dev.scuttle.inventory.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.error.ErrorLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val googleLoading: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
)

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val repository: AuthRepository,
        private val errorLogger: ErrorLogger,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AuthUiState(authenticated = repository.isAuthenticated()))
        val state: StateFlow<AuthUiState> = _state.asStateFlow()

        init {
            // React to a mid-session token loss (a 401 clears the token off the UI
            // thread): flip authenticated=false so MainActivity redirects to login,
            // instead of leaving the user on authed screens where every call 401s.
            viewModelScope.launch {
                repository.sessionActive.collect { active ->
                    if (!active) _state.update { it.copy(authenticated = false) }
                }
            }
        }

        fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

        fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

        fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

        fun toggleMode() =
            _state.update {
                it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN, error = null)
            }

        fun signOut() {
            viewModelScope.launch {
                runCatching { repository.logout() }
                _state.value = AuthUiState()
            }
        }

        fun onGoogleLoading() = _state.update { it.copy(googleLoading = true, error = null) }

        fun onGoogleError(message: String?) = _state.update { it.copy(googleLoading = false, error = message) }

        fun loginWithGoogle(idToken: String) {
            viewModelScope.launch {
                _state.update { it.copy(googleLoading = true, error = null) }
                val result = runCatching { repository.loginWithGoogle(idToken) }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(googleLoading = false, authenticated = true) },
                        onFailure = { error ->
                            errorLogger.log("google_sign_in", error.message)
                            state.copy(googleLoading = false, error = error.toGoogleAuthErrorMessage())
                        },
                    )
                }
            }
        }

        fun loginWithGoogleCode(
            code: String,
            codeVerifier: String,
            redirectUri: String,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(googleLoading = true, error = null) }
                val result = runCatching { repository.loginWithGoogleCode(code, codeVerifier, redirectUri) }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(googleLoading = false, authenticated = true) },
                        onFailure = {
                                error ->
                            state.copy(googleLoading = false, error = error.toGoogleAuthErrorMessage())
                        },
                    )
                }
            }
        }

        private fun Throwable.toAuthErrorMessage(mode: AuthMode): String =
            when {
                this is IOException -> "Can't reach the server. Check your connection and try again."
                this is HttpException ->
                    when (code()) {
                        401 -> "Incorrect email or password."
                        409 -> "An account with this email already exists."
                        422 -> "Please check your details and try again."
                        in 500..599 -> "Server error. Please try again later."
                        else -> "Authentication failed (${code()})."
                    }
                else -> message ?: "Authentication failed."
            }

        private fun Throwable.toGoogleAuthErrorMessage(): String =
            when {
                this is IOException -> "Can't reach the server. Check your connection and try again."
                this is HttpException ->
                    when (code()) {
                        401 -> "Google sign-in failed. Please try again."
                        in 500..599 -> "Server error. Please try again later."
                        else -> "Google sign-in failed (${code()})."
                    }
                else -> message ?: "Google sign-in failed."
            }

        fun submit() {
            val current = _state.value
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                val result =
                    runCatching {
                        when (current.mode) {
                            AuthMode.LOGIN -> repository.login(current.email.trim(), current.password)
                            AuthMode.REGISTER ->
                                repository.register(
                                    current.name.trim(),
                                    current.email.trim(),
                                    current.password,
                                )
                        }
                    }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false, authenticated = true) },
                        onFailure = { error ->
                            errorLogger.log("auth_${current.mode.name.lowercase()}", error.message)
                            state.copy(loading = false, error = error.toAuthErrorMessage(current.mode))
                        },
                    )
                }
            }
        }
    }
