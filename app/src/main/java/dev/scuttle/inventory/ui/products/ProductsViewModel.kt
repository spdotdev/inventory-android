package dev.scuttle.inventory.ui.products

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

data class ProductsUiState(
    val loading: Boolean = false,
    val products: List<ProductDto> = emptyList(),
    val newName: String = "",
    val error: String? = null,
)

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val repository: ProductRepository,
) : ViewModel() {

    private var householdId: Long? = null
    private var shelfId: Long? = null

    private val _state = MutableStateFlow(ProductsUiState())
    val state: StateFlow<ProductsUiState> = _state.asStateFlow()

    fun load(householdId: Long, shelfId: Long) {
        if (this.householdId == householdId && this.shelfId == shelfId && _state.value.products.isNotEmpty()) return
        this.householdId = householdId
        this.shelfId = shelfId
        refresh()
    }

    fun onNewNameChange(value: String) = _state.update { it.copy(newName = value, error = null) }

    fun refresh() {
        val h = householdId ?: return
        val s = shelfId ?: return
        launch {
            val products = repository.list(h, s)
            _state.update { it.copy(products = products) }
        }
    }

    fun create() {
        val h = householdId ?: return
        val s = shelfId ?: return
        val name = _state.value.newName.trim()
        if (name.isEmpty()) return
        launch {
            repository.create(h, s, name, 0)
            _state.update { it.copy(newName = "", products = repository.list(h, s)) }
        }
    }

    fun increment(productId: Long) = mutateOne { h, s -> repository.add(h, s, productId, 1) }

    fun decrement(productId: Long) = mutateOne { h, s -> repository.remove(h, s, productId, 1) }

    private fun mutateOne(block: suspend (Long, Long) -> ProductDto) {
        val h = householdId ?: return
        val s = shelfId ?: return
        launch {
            val updated = block(h, s)
            _state.update { state ->
                state.copy(products = state.products.map { if (it.id == updated.id) updated else it })
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { block() }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(loading = false) },
                    onFailure = { error -> state.copy(loading = false, error = error.message ?: "Something went wrong.") },
                )
            }
        }
    }
}
