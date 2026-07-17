package dev.scuttle.inventory.ui.products

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val loading: Boolean = false,
    // Only load() (the pull-to-refresh target) flips this; save/upload/delete use
    // `loading` alone, so the pull spinner doesn't fire on those mutations.
    val refreshing: Boolean = false,
    val product: ProductDto? = null,
    val error: String? = null,
    // Distinct from [error] (mutation failures, shown as a Snackbar): a LOAD
    // failure needs a PERSISTENT inline idiom, since a missed/dismissed
    // snackbar would otherwise leave a blank screen with zero explanation (M4).
    val loadError: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    /** The batch just deleted, for the Undo snackbar. Cleared once consumed. */
    val lastBatchId: String? = null,
    /**
     * One-shot result of the last undoDelete() call — null while none is pending.
     * The screen turns this into the matching localized snackbar (delete_undone /
     * delete_undo_failed) and calls [ProductDetailViewModel.consumeUndoResult].
     */
    val undoResult: UndoOutcome? = null,
)

@HiltViewModel
class ProductDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: ProductRepository,
        private val restoreRepository: RestoreRepository,
    ) : ViewModel() {
        private val householdId: Long =
            savedStateHandle["householdId"] ?: run {
                android.util.Log.e("ProductDetailVM", "householdId missing from nav args")
                -1L
            }
        private val shelfId: Long =
            savedStateHandle["shelfId"] ?: run {
                android.util.Log.e("ProductDetailVM", "shelfId missing from nav args")
                -1L
            }
        private val productId: Long =
            savedStateHandle["productId"] ?: run {
                android.util.Log.e("ProductDetailVM", "productId missing from nav args")
                -1L
            }

        private val _state = MutableStateFlow(ProductDetailUiState())
        val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

        init {
            if (householdId == -1L || shelfId == -1L || productId == -1L) {
                _state.update { it.copy(loadError = "Invalid navigation — missing product ID.") }
            } else {
                load()
            }
        }

        fun load() {
            if (householdId == -1L || shelfId == -1L || productId == -1L) return
            viewModelScope.launch {
                _state.update { it.copy(loading = true, refreshing = true, loadError = null) }
                runCatching { repository.list(householdId, shelfId) }
                    .onSuccess { products ->
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                product =
                                    products.find { p ->
                                        p.id == productId
                                    },
                            )
                        }
                    }.onFailure { e ->
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                loadError = e.toUserMessage("Failed to load product."),
                            )
                        }
                    }
            }
        }

        fun save(edit: ProductEdit) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                runCatching {
                    repository.update(householdId, shelfId, productId, edit)
                }.onSuccess { updated ->
                    _state.update { it.copy(loading = false, product = updated, saved = true) }
                }.onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.toUserMessage("Failed to save.")) }
                }
            }
        }

        fun uploadImage(
            imageUri: Uri,
            mimeType: String,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                runCatching {
                    repository.uploadImage(householdId, shelfId, productId, imageUri, mimeType)
                }.onSuccess { updated ->
                    _state.update { it.copy(loading = false, product = updated) }
                }.onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.toUserMessage("Failed to upload image.")) }
                }
            }
        }

        fun delete() {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                runCatching { repository.delete(householdId, shelfId, productId) }
                    .onSuccess { batchId ->
                        // batchId is server-minted (ProductDeleteResponse) — captured
                        // here so the screen can offer Undo before it navigates away.
                        _state.update { it.copy(loading = false, deleted = true, lastBatchId = batchId) }
                    }.onFailure { e ->
                        _state.update { it.copy(loading = false, error = e.toUserMessage("Failed to delete.")) }
                    }
            }
        }

        fun undoDelete() {
            val batchId = _state.value.lastBatchId ?: return
            viewModelScope.launch {
                // A 409 here means the batch was already restored (another device, a
                // double-tap) or permanently removed past the undo window — NOT a
                // generic failure, so it does not populate state.error; the screen
                // turns undoResult into the specific message instead.
                val result = runCatching { restoreRepository.restore(householdId, batchId) }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                if (result.isSuccess) {
                    _state.update { it.copy(lastBatchId = null, undoResult = UndoOutcome.SUCCESS) }
                } else {
                    _state.update { it.copy(undoResult = UndoOutcome.FAILURE) }
                }
            }
        }

        fun consumeLastBatch() = _state.update { it.copy(lastBatchId = null) }

        fun consumeUndoResult() = _state.update { it.copy(undoResult = null) }

        /** Clears the error after it's been shown once (e.g. surfaced as a Snackbar). */
        fun consumeError() = _state.update { it.copy(error = null) }
    }
