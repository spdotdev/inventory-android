package dev.scuttle.inventory.ui.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.DeletedBatchDto
import dev.scuttle.inventory.data.error.toUserMessageRes
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.HttpURLConnection
import javax.inject.Inject

data class DeletedBatchesUiState(
    val loading: Boolean = false,
    val batches: List<DeletedBatchDto> = emptyList(),
    // H3 convention (see MembersUiState): an R.string.* id, resolved via
    // stringResource() in the composable, not a raw literal.
    val errorRes: Int? = null,
    // A 409 (already restored / permanently removed elsewhere) is not a generic
    // failure — surfaced as its own message so the screen doesn't say "Something
    // went wrong" for a stale-list race that a refresh already fixes.
    val restoreConflict: Boolean = false,
)

/**
 * Recently-deleted browser (round-2 audit US-closer): lists every restorable
 * deletion batch for a household, regardless of which surface — API/Android or
 * web — minted it (server queries the soft-deleted rows directly, not a
 * per-surface log; see Support\RecentlyDeleted on the backend). Closes the gap
 * Android's own Undo snackbar leaves once it times out or the app restarts.
 */
@HiltViewModel
class DeletedBatchesViewModel
    @Inject
    constructor(
        private val repository: RestoreRepository,
        private val hierarchyStore: HierarchyStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DeletedBatchesUiState())
        val state: StateFlow<DeletedBatchesUiState> = _state.asStateFlow()

        private var householdId: Long? = null

        fun load(householdId: Long) {
            this.householdId = householdId
            launchLoading {
                _state.update { it.copy(batches = repository.listDeleted(householdId)) }
            }
        }

        fun restore(batch: String) {
            val id = householdId ?: return
            launchLoading {
                val result = runCatching { repository.restore(id, batch) }
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                when {
                    result.isSuccess -> {
                        _state.update { it.copy(batches = it.batches.filterNot { b -> b.batch == batch }) }
                        hierarchyStore.refresh()
                    }
                    error is HttpException && error.code() == HttpURLConnection.HTTP_CONFLICT -> {
                        // Someone else (another device, or this batch aging past the
                        // retention window) already resolved it — drop the dead
                        // "Restore" button instead of leaving it clickable, and
                        // surface a conflict message rather than a generic failure.
                        _state.update {
                            it.copy(
                                batches = it.batches.filterNot { b -> b.batch == batch },
                                restoreConflict = true,
                            )
                        }
                    }
                    else -> {
                        // A transient failure (network, 5xx) — keep the batch in the
                        // list so the user can retry, and let launchLoading's normal
                        // errorRes path surface the message.
                        error?.let { throw it }
                    }
                }
            }
        }

        fun consumeRestoreConflict() = _state.update { it.copy(restoreConflict = false) }

        private fun launchLoading(block: suspend () -> Unit) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                val result = runCatching { block() }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                _state.update { state ->
                    result.fold(
                        onSuccess = { state.copy(loading = false) },
                        onFailure = { e ->
                            state.copy(loading = false, errorRes = e.toUserMessageRes(R.string.error_generic))
                        },
                    )
                }
            }
        }
    }
