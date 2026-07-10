package dev.scuttle.inventory.ui.households

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.household.HouseholdRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdsUiState(
    val loading: Boolean = false,
    // Only a user-initiated refresh() flips this; create/leave use `loading` alone,
    // so the pull-to-refresh spinner doesn't fire on mutations.
    val refreshing: Boolean = false,
    val households: List<HouseholdDto> = emptyList(),
    val newName: String = "",
    val error: String? = null,
)

@HiltViewModel
class HouseholdsViewModel
    @Inject
    constructor(
        private val repository: HouseholdRepository,
        private val hierarchyStore: HierarchyStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HouseholdsUiState())
        val state: StateFlow<HouseholdsUiState> = _state.asStateFlow()

        init {
            val cached = repository.getCached()
            if (cached != null) {
                _state.update { it.copy(households = cached) }
                refreshSilent()
            } else {
                refresh()
            }
        }

        fun onNewNameChange(value: String) = _state.update { it.copy(newName = value.take(50), error = null) }

        fun refresh() =
            launchLoading(refreshing = true) {
                _state.update { it.copy(households = repository.list()) }
            }

        private fun refreshSilent() {
            viewModelScope.launch {
                runCatching { repository.list() }
                    .onSuccess { households -> _state.update { it.copy(households = households) } }
            }
        }

        fun create() {
            val name = _state.value.newName.trim()
            if (name.isEmpty()) return
            launchLoading {
                repository.create(name)
                _state.update { it.copy(newName = "") }
                _state.update { it.copy(households = repository.list()) }
                // Keep the drawer/home/dashboard (driven by HierarchyStore) in sync — a
                // new household would otherwise stay invisible there until a manual
                // pull-to-refresh or relaunch (X4).
                hierarchyStore.refresh()
            }
        }

        fun leave(householdId: Long) =
            launchLoading {
                repository.leave(householdId)
                _state.update { it.copy(households = repository.list()) }
                hierarchyStore.refresh()
            }

        /** Persist the chosen theme keys (null = back to the derived default). */
        fun updateTheme(
            householdId: Long,
            color: String?,
            icon: String?,
        ) = launchLoading {
            val updated = repository.updateTheme(householdId, color, icon)
            _state.update { s ->
                s.copy(households = s.households.map { if (it.id == updated.id) updated else it })
            }
            // Drawer avatars read HierarchyStore, not this VM's list (X4).
            hierarchyStore.refresh()
        }

        private fun launchLoading(
            refreshing: Boolean = false,
            block: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, refreshing = refreshing, error = null) }
                val result = runCatching { block() }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false, refreshing = false) },
                        onFailure = {
                                e ->
                            state.copy(
                                loading = false,
                                refreshing = false,
                                error = e.toUserMessage("Something went wrong."),
                            )
                        },
                    )
                }
            }
        }
    }
