package dev.scuttle.inventory.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoveTarget(
    val shelfId: Long,
    val label: String,
)

data class ProductsUiState(
    val loading: Boolean = false,
    val products: List<ProductDto> = emptyList(),
    val newName: String = "",
    val error: String? = null,
    val movingProductId: Long? = null,
    val moveTargets: List<MoveTarget> = emptyList(),
)

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository,
    private val shelfRepository: ShelfRepository,
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
            val products = productRepository.list(h, s)
            _state.update { it.copy(products = products) }
        }
    }

    fun create() {
        val h = householdId ?: return
        val s = shelfId ?: return
        val name = _state.value.newName.trim()
        if (name.isEmpty()) return
        launch {
            productRepository.create(h, s, name, 0)
            _state.update { it.copy(newName = "", products = productRepository.list(h, s)) }
        }
    }

    fun increment(productId: Long) = mutateOne { h, s -> productRepository.add(h, s, productId, 1) }

    fun decrement(productId: Long) = mutateOne { h, s -> productRepository.remove(h, s, productId, 1) }

    fun startMove(productId: Long) {
        val h = householdId ?: return
        val s = shelfId ?: return
        _state.update { it.copy(movingProductId = productId, moveTargets = emptyList()) }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching {
                val targets = mutableListOf<MoveTarget>()
                for (location in locationRepository.list(h)) {
                    for (shelf in shelfRepository.list(h, location.id)) {
                        if (shelf.id != s) targets.add(MoveTarget(shelf.id, "${location.name} › ${shelf.name}"))
                    }
                }
                targets
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { targets -> state.copy(loading = false, moveTargets = targets) },
                    onFailure = { e -> state.copy(loading = false, error = e.message ?: "Couldn't load shelves.", movingProductId = null, moveTargets = emptyList()) },
                )
            }
        }
    }

    fun cancelMove() = _state.update { it.copy(movingProductId = null, moveTargets = emptyList()) }

    fun confirmMove(targetShelfId: Long) {
        val h = householdId ?: return
        val s = shelfId ?: return
        val productId = _state.value.movingProductId ?: return
        launch {
            productRepository.move(h, s, productId, targetShelfId)
            // The product left this shelf — drop it from the current list.
            _state.update {
                it.copy(
                    products = it.products.filterNot { product -> product.id == productId },
                    movingProductId = null,
                    moveTargets = emptyList(),
                )
            }
        }
    }

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
