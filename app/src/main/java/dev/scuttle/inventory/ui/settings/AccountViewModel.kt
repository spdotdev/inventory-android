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

data class AccountUiState(
    val loading: Boolean = false,
    val user: UserDto? = null,
    // H3: an R.string.* id, not a raw literal — resolved via stringResource() by the screen.
    val errorRes: Int? = null,
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
                _state.update { it.copy(loading = true, errorRes = null) }
                val result = runCatching { repository.me() }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { user -> state.copy(loading = false, user = user) },
                        onFailure = { e ->
                            state.copy(
                                loading = false,
                                errorRes = e.toUserMessageRes(R.string.error_failed_to_load_account),
                            )
                        },
                    )
                }
            }
        }
    }
