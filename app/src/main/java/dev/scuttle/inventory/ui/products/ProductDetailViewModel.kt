package dev.scuttle.inventory.ui.products

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.error.toUserMessageRes
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
    // H3: an R.string.* id, not a raw literal — resolved via stringResource() in the composable.
    val errorRes: Int? = null,
    // Distinct from [errorRes] (mutation failures, shown as a Snackbar): a LOAD
    // failure needs a PERSISTENT inline idiom, since a missed/dismissed
    // snackbar would otherwise leave a blank screen with zero explanation (M4).
    val loadErrorRes: Int? = null,
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
    /**
     * H2: bumps on every +/- quantity mutation completing, success OR failure — see
     * `ProductsUiState.quantityMutationEpoch` (ProductsViewModel's equivalent) for why: a
     * failed mutation never changes `product.quantity`, so the stepper's local optimistic
     * count needs this second reset signal or it's left wrong on screen indefinitely.
     */
    val quantityMutationEpoch: Int = 0,
    /**
     * One-shot: true right after a +/- quantity mutation failed. The screen shows the specific
     * `quantity_update_failed` string instead of the generic `error` snackbar, then calls
     * [ProductDetailViewModel.consumeQuantityMutationFailed].
     */
    val quantityMutationFailed: Boolean = false,
)

@HiltViewModel
class ProductDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: ProductRepository,
        private val restoreRepository: RestoreRepository,
        private val hierarchyStore: HierarchyStore,
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
                _state.update { it.copy(loadErrorRes = R.string.error_invalid_navigation_missing_product_id) }
            } else {
                load()
            }
            // GAP6-M1: a remote household.changed ping (another member edits/moves/
            // deletes this exact product) arrives via LiveUpdates as
            // hierarchyStore.refresh() only — nothing previously re-fetched this
            // screen's product, so it went stale until a manual pull-to-refresh.
            // Mirrors HouseholdsViewModel's observeHierarchyStore() (bc0ea63): only
            // react once the store's own refresh has LANDED (!loading), and only
            // while this VM has no mutation of its own in flight (!loading on
            // _state) so a remote ping never clobbers an in-flight local save/
            // increment/decrement/delete.
            viewModelScope.launch {
                hierarchyStore.state.collect { hierarchyState ->
                    if (hierarchyState.loading || _state.value.loading) return@collect
                    load()
                }
            }
        }

        fun load() {
            if (householdId == -1L || shelfId == -1L || productId == -1L) return
            viewModelScope.launch {
                _state.update { it.copy(loading = true, refreshing = true, loadErrorRes = null) }
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
                                loadErrorRes = e.toUserMessageRes(R.string.error_failed_to_load_product),
                            )
                        }
                    }
            }
        }

        fun save(edit: ProductEdit) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                runCatching {
                    repository.update(householdId, shelfId, productId, edit)
                }.onSuccess { updated ->
                    _state.update { it.copy(loading = false, product = updated, saved = true) }
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            errorRes = e.toUserMessageRes(R.string.error_failed_to_save),
                        )
                    }
                }
            }
        }

        /**
         * +1 / -1 stock stepper (GAP-5 H8), mirroring ProductsPane's
         * increment/decrement — the detail screen never offered this before,
         * even though it shows product.quantity nowhere either. Uses the same
         * mutation shape as [save]/[uploadImage] (loading + runCatching, no
         * launchLoading helper — this class doesn't have one), replacing
         * [ProductDetailUiState.product] with the server's response so
         * quantity always reflects the authoritative value, never a locally
         * guessed one.
         */
        fun increment(amount: Int = 1) {
            val current = _state.value.product ?: return
            if (amount <= 0) return
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                runCatching { repository.add(householdId, shelfId, current.id, amount) }
                    .onSuccess { updated ->
                        _state.update {
                            it.copy(
                                loading = false,
                                product = updated,
                                quantityMutationEpoch =
                                    it.quantityMutationEpoch + 1,
                            )
                        }
                    }.onFailure {
                        // H2: no generic `error` here — the screen shows the specific
                        // quantity_update_failed string instead, and quantityMutationEpoch
                        // resets the stepper's optimistic count even though product.quantity
                        // (which would otherwise be the only reset signal) never changed.
                        _state.update {
                            it.copy(
                                loading = false,
                                quantityMutationFailed = true,
                                quantityMutationEpoch = it.quantityMutationEpoch + 1,
                            )
                        }
                    }
            }
        }

        fun decrement(amount: Int = 1) {
            val current = _state.value.product ?: return
            if (current.quantity <= 0 || amount <= 0) return
            val clamped = minOf(amount, current.quantity)
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                runCatching { repository.remove(householdId, shelfId, current.id, clamped) }
                    .onSuccess { updated ->
                        _state.update {
                            it.copy(
                                loading = false,
                                product = updated,
                                quantityMutationEpoch =
                                    it.quantityMutationEpoch + 1,
                            )
                        }
                    }.onFailure {
                        _state.update {
                            it.copy(
                                loading = false,
                                quantityMutationFailed = true,
                                quantityMutationEpoch = it.quantityMutationEpoch + 1,
                            )
                        }
                    }
            }
        }

        fun uploadImage(
            imageUri: Uri,
            mimeType: String,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                runCatching {
                    repository.uploadImage(householdId, shelfId, productId, imageUri, mimeType)
                }.onSuccess { updated ->
                    _state.update { it.copy(loading = false, product = updated) }
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            errorRes = e.toUserMessageRes(R.string.error_failed_to_upload_image),
                        )
                    }
                }
            }
        }

        fun delete() {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorRes = null) }
                runCatching { repository.delete(householdId, shelfId, productId) }
                    .onSuccess { batchId ->
                        // batchId is server-minted (ProductDeleteResponse) — captured
                        // here so the screen can offer Undo before it navigates away.
                        _state.update { it.copy(loading = false, deleted = true, lastBatchId = batchId) }
                    }.onFailure { e ->
                        _state.update {
                            it.copy(
                                loading = false,
                                errorRes = e.toUserMessageRes(R.string.error_failed_to_delete),
                            )
                        }
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
        fun consumeError() = _state.update { it.copy(errorRes = null) }

        /** Clears the one-shot quantity-mutation-failed flag after the UI has shown it. */
        fun consumeQuantityMutationFailed() = _state.update { it.copy(quantityMutationFailed = false) }
    }
