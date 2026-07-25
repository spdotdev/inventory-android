package dev.scuttle.inventory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.UserDto
import dev.scuttle.inventory.data.error.toUserMessageRes
import dev.scuttle.inventory.data.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Matches UpdateProfileRequest's server-side max:255 validation on both name and email. */
private const val MAX_FIELD_LENGTH = 255

data class AccountUiState(
    val loading: Boolean = false,
    val user: UserDto? = null,
    // Draft edits — seeded from `user` once it loads (see load()'s onSuccess), then
    // driven by the screen's text fields until save() sends them.
    val name: String = "",
    val email: String = "",
    // H3: an R.string.* id, not a raw literal — resolved via stringResource() by the screen.
    // A LOAD failure needs a PERSISTENT inline idiom (same idiom as ProductDetailScreen's
    // loadErrorRes) — a missed/dismissed snackbar would otherwise leave the screen blank
    // with no explanation and no working retry (retrying a save error should re-save, not
    // re-load, so the two failure modes can't share one field).
    val loadErrorRes: Int? = null,
    // A SAVE failure, shown as a transient Snackbar; distinct from [loadErrorRes] above.
    val errorRes: Int? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AccountUiState())
        val state: StateFlow<AccountUiState> = _state.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, loadErrorRes = null) }
                val result = runCatching { repository.me() }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { user ->
                            state.copy(loading = false, user = user, name = user.name, email = user.email)
                        },
                        onFailure = { e ->
                            state.copy(
                                loading = false,
                                loadErrorRes = e.toUserMessageRes(R.string.error_failed_to_load_account),
                            )
                        },
                    )
                }
            }
        }

        fun onNameChange(value: String) = _state.update { it.copy(name = value.take(MAX_FIELD_LENGTH), saved = false) }

        fun onEmailChange(value: String) =
            _state.update { it.copy(email = value.take(MAX_FIELD_LENGTH), saved = false) }

        fun save() {
            val name = _state.value.name.trim()
            val email = _state.value.email.trim()
            if (name.isEmpty() || email.isEmpty()) return
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null, saved = false) }
                val result = runCatching { repository.update(name, email) }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { user ->
                            state.copy(
                                loading = false,
                                user = user,
                                name = user.name,
                                email = user.email,
                                saved = true,
                            )
                        },
                        onFailure = { e ->
                            state.copy(loading = false, errorRes = e.toUserMessageRes(R.string.error_failed_to_save))
                        },
                    )
                }
            }
        }

        /** Clears the error after it's been shown once (e.g. surfaced as a Snackbar). */
        fun consumeError() = _state.update { it.copy(errorRes = null) }

        /** Clears the one-shot saved flag after the UI has shown it. */
        fun consumeSaved() = _state.update { it.copy(saved = false) }
    }
