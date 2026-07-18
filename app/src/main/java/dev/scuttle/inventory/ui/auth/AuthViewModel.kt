package dev.scuttle.inventory.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
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

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_UNPROCESSABLE = 422
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERRORS = 500..599

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val googleLoading: Boolean = false,
    val error: String? = null,
    // Localized server-side failures; `error` above stays for client-side
    // Google (Credential Manager) messages, which arrive as plain strings.
    val errorRes: Int? = null,
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

        fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null, errorRes = null) }

        fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null, errorRes = null) }

        fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null, errorRes = null) }

        fun toggleMode() =
            _state.update {
                it.copy(
                    mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
                    error = null,
                    errorRes = null,
                )
            }

        fun signOut() {
            viewModelScope.launch {
                runCatching { repository.logout() }
                _state.value = AuthUiState()
            }
        }

        fun onGoogleLoading() = _state.update { it.copy(googleLoading = true, error = null, errorRes = null) }

        fun onGoogleError(message: String?) = _state.update { it.copy(googleLoading = false, error = message) }

        fun loginWithGoogle(idToken: String) {
            viewModelScope.launch {
                _state.update { it.copy(googleLoading = true, error = null, errorRes = null) }
                val result = runCatching { repository.loginWithGoogle(idToken) }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(googleLoading = false, authenticated = true) },
                        onFailure = { error ->
                            errorLogger.log("google_sign_in", error.message)
                            state.copy(googleLoading = false, errorRes = error.toGoogleAuthErrorRes())
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
                _state.update { it.copy(googleLoading = true, error = null, errorRes = null) }
                val result = runCatching { repository.loginWithGoogleCode(code, codeVerifier, redirectUri) }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(googleLoading = false, authenticated = true) },
                        onFailure = { error ->
                            state.copy(googleLoading = false, errorRes = error.toGoogleAuthErrorRes())
                        },
                    )
                }
            }
        }

        // GAP-8: these were hardcoded EN strings — NL users saw English on
        // every auth failure. The dead 409 branch is gone too (the backend
        // returns duplicate-email as a 422 via its unique: rule, never 409),
        // and 429 now gets its throttle message instead of a raw code.
        private fun Throwable.toAuthErrorRes(): Int =
            when {
                this is IOException -> R.string.error_network_unreachable
                this is HttpException ->
                    when (code()) {
                        HTTP_UNAUTHORIZED -> R.string.error_incorrect_credentials
                        HTTP_UNPROCESSABLE -> R.string.error_check_details
                        HTTP_TOO_MANY_REQUESTS -> R.string.error_too_many_requests
                        in HTTP_SERVER_ERRORS -> R.string.error_server
                        else -> R.string.error_auth_failed
                    }
                else -> R.string.error_auth_failed
            }

        private fun Throwable.toGoogleAuthErrorRes(): Int =
            when {
                this is IOException -> R.string.error_network_unreachable
                this is HttpException ->
                    when (code()) {
                        HTTP_TOO_MANY_REQUESTS -> R.string.error_too_many_requests
                        in HTTP_SERVER_ERRORS -> R.string.error_server
                        else -> R.string.error_google_signin_failed
                    }
                else -> R.string.error_google_signin_failed
            }

        fun submit() {
            val current = _state.value
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null, errorRes = null) }
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
                            state.copy(loading = false, errorRes = error.toAuthErrorRes())
                        },
                    )
                }
            }
        }
    }
