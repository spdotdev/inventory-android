package dev.scuttle.inventory.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.dto.MemberDto
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.member.MemberRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MembersUiState(
    val loading: Boolean = false,
    val members: List<MemberDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MembersViewModel
    @Inject
    constructor(
        private val repository: MemberRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MembersUiState())
        val state: StateFlow<MembersUiState> = _state.asStateFlow()

        private var householdId: Long? = null

        fun load(householdId: Long) {
            this.householdId = householdId
            launchLoading {
                _state.update { it.copy(members = repository.list(householdId)) }
            }
        }

        fun promote(userId: Long) = setRole(userId, "admin")

        fun demote(userId: Long) = setRole(userId, "member")

        private fun setRole(
            userId: Long,
            role: String,
        ) {
            val id = householdId ?: return
            launchLoading {
                val updated = repository.updateRole(id, userId, role)
                _state.update { state ->
                    state.copy(members = state.members.map { if (it.id == userId) updated else it })
                }
            }
        }

        fun remove(userId: Long) {
            val id = householdId ?: return
            launchLoading {
                repository.remove(id, userId)
                _state.update { state -> state.copy(members = state.members.filter { it.id != userId }) }
            }
        }

        fun transferOwnership(userId: Long) {
            val id = householdId ?: return
            launchLoading {
                repository.transferOwnership(id, userId)
                _state.update { it.copy(members = repository.list(id)) }
            }
        }

        private fun launchLoading(block: suspend () -> Unit) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                val result = runCatching { block() }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false) },
                        onFailure = { e ->
                            state.copy(loading = false, error = e.toUserMessage("Something went wrong."))
                        },
                    )
                }
            }
        }
    }
