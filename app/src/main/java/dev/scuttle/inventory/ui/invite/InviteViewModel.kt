package dev.scuttle.inventory.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.error.toUserMessageRes
import dev.scuttle.inventory.data.invite.InviteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InviteUiState(
    val loading: Boolean = false,
    val code: String = "",
    val link: String = "",
    // H3: an R.string.* id, not a raw literal — resolved via stringResource() in the composable.
    val errorRes: Int? = null,
)

@HiltViewModel
class InviteViewModel
    @Inject
    constructor(
        private val repository: InviteRepository,
    ) : ViewModel() {
        private var householdId: Long? = null

        private val _state = MutableStateFlow(InviteUiState())
        val state: StateFlow<InviteUiState> = _state.asStateFlow()

        fun load(householdId: Long) {
            if (this.householdId == householdId && _state.value.code.isNotEmpty()) return
            this.householdId = householdId
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                val result = runCatching { repository.invite(householdId) }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { invite -> state.copy(loading = false, code = invite.code, link = invite.link) },
                        onFailure = { error ->
                            state.copy(
                                loading = false,
                                errorRes = error.toUserMessageRes(R.string.error_failed_to_load_invite),
                            )
                        },
                    )
                }
            }
        }
    }
