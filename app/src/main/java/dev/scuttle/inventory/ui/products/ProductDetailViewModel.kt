package dev.scuttle.inventory.ui.products

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.scuttle.inventory.data.error.toUserMessage
import javax.inject.Inject

data class ProductDetailUiState(
    val loading: Boolean = false,
    // Only load() (the pull-to-refresh target) flips this; save/upload/delete use
    // `loading` alone, so the pull spinner doesn't fire on those mutations.
    val refreshing: Boolean = false,
    val product: ProductDto? = null,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
) : ViewModel() {

    private val householdId: Long = savedStateHandle["householdId"] ?: run {
        android.util.Log.e("ProductDetailVM", "householdId missing from nav args")
        -1L
    }
    private val shelfId: Long = savedStateHandle["shelfId"] ?: run {
        android.util.Log.e("ProductDetailVM", "shelfId missing from nav args")
        -1L
    }
    private val productId: Long = savedStateHandle["productId"] ?: run {
        android.util.Log.e("ProductDetailVM", "productId missing from nav args")
        -1L
    }

    private val _state = MutableStateFlow(ProductDetailUiState())
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    init {
        if (householdId == -1L || shelfId == -1L || productId == -1L) {
            _state.update { it.copy(error = "Invalid navigation — missing product ID.") }
        } else {
            load()
        }
    }

    fun load() {
        if (householdId == -1L || shelfId == -1L || productId == -1L) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, refreshing = true, error = null) }
            runCatching { repository.list(householdId, shelfId) }
                .onSuccess { products ->
                    _state.update { it.copy(loading = false, refreshing = false, product = products.find { p -> p.id == productId }) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, refreshing = false, error = e.toUserMessage("Failed to load product.")) }
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

    fun uploadImage(imageUri: Uri, mimeType: String) {
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
                .onSuccess { _state.update { it.copy(loading = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toUserMessage("Failed to delete.")) } }
        }
    }

    /** Clears the error after it's been shown once (e.g. surfaced as a Snackbar). */
    fun consumeError() = _state.update { it.copy(error = null) }
}
