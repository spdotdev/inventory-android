package dev.scuttle.inventory.ui.products

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.product.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val loading: Boolean = false,
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

    private val householdId: Long = checkNotNull(savedStateHandle["householdId"])
    private val shelfId: Long = checkNotNull(savedStateHandle["shelfId"])
    private val productId: Long = checkNotNull(savedStateHandle["productId"])

    private val _state = MutableStateFlow(ProductDetailUiState())
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.list(householdId, shelfId) }
                .onSuccess { products ->
                    _state.update { it.copy(loading = false, product = products.find { p -> p.id == productId }) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to load product.") }
                }
        }
    }

    fun save(name: String, description: String?, code: String?, isMandatory: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                repository.update(householdId, shelfId, productId, name, description, code, isMandatory)
            }.onSuccess { updated ->
                _state.update { it.copy(loading = false, product = updated, saved = true) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to save.") }
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
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to upload image.") }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.delete(householdId, shelfId, productId) }
                .onSuccess { _state.update { it.copy(loading = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Failed to delete.") } }
        }
    }
}
