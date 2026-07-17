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
    // Incremented (never reset) each time transferOwnership() succeeds. The
    // caller (MembersScreen, via MainActivity) observes this as a one-shot
    // event to refresh whatever ELSE derives "is the viewer the owner" —
    // this ViewModel has no notion of the viewer's own user id (see
    // MembersScreen's viewerRole doc comment), so it can't fix the caller's
    // stale role itself, but it CAN signal "your role may have just changed"
    // in lockstep with its own (correctly refreshed) member list, closing the
    // staleness window to a single recomposition instead of leaving the
    // caller's prop stale indefinitely.
    val ownershipTransferCount: Int = 0,
    // One-shot event: set the moment a promote/demote lands, cleared once the
    // screen has shown its snackbar (consumeRoleChangeEvent). Carries what the
    // snackbar needs (who, new role, and the role to revert to on Undo) rather
    // than just a counter like [ownershipTransferCount] — Undo needs the actual
    // previous role, not merely a "something changed" signal.
    val roleChangeEvent: RoleChangeEvent? = null,
)

data class RoleChangeEvent(
    val userId: Long,
    val memberName: String,
    val newRole: String,
    val previousRole: String,
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

        fun promote(userId: Long) = setRole(userId, "admin", emitEvent = true)

        fun demote(userId: Long) = setRole(userId, "member", emitEvent = true)

        /**
         * Applies the inverse role change from a [RoleChangeEvent] shown as an Undo
         * action on the promote/demote snackbar (M1). Does not itself emit a new
         * [RoleChangeEvent] — Undo is a correction, not a fresh action the user
         * should be offered a snackbar+Undo for in turn.
         */
        fun undoRoleChange(event: RoleChangeEvent) = setRole(event.userId, event.previousRole, emitEvent = false)

        private fun setRole(
            userId: Long,
            role: String,
            emitEvent: Boolean,
        ) {
            val id = householdId ?: return
            val previousRole = _state.value.members.firstOrNull { it.id == userId }?.role
            launchLoading {
                val updated = repository.updateRole(id, userId, role)
                _state.update { state ->
                    state.copy(
                        members = state.members.map { if (it.id == userId) updated else it },
                        roleChangeEvent =
                            if (emitEvent && previousRole != null) {
                                RoleChangeEvent(userId, updated.name, role, previousRole)
                            } else {
                                state.roleChangeEvent
                            },
                    )
                }
            }
        }

        fun consumeRoleChangeEvent() = _state.update { it.copy(roleChangeEvent = null) }

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
                _state.update {
                    it.copy(
                        members = repository.list(id),
                        ownershipTransferCount = it.ownershipTransferCount + 1,
                    )
                }
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
