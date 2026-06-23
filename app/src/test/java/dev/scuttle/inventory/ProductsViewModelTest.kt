package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.ui.products.ProductsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeProductRepository : ProductRepository {
        val items = mutableListOf<ProductDto>()
        var failList = false

        override suspend fun list(householdId: Long, shelfId: Long): List<ProductDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int): ProductDto {
            val dto = ProductDto(id = (items.size + 1).toLong(), name = name, quantity = quantity, shelf_id = shelfId)
            items.add(dto)
            return dto
        }

        override suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
            adjust(productId, amount)

        override suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
            adjust(productId, -amount)

        override suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long): ProductDto =
            items.first { it.id == productId }

        private fun adjust(productId: Long, delta: Int): ProductDto {
            val index = items.indexOfFirst { it.id == productId }
            val updated = items[index].let { it.copy(quantity = (it.quantity + delta).coerceAtLeast(0)) }
            items[index] = updated
            return updated
        }
    }

    @Test
    fun load_populates_products() = runTest {
        val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
        val viewModel = ProductsViewModel(repo)

        viewModel.load(householdId = 1, shelfId = 1)

        assertEquals(1, viewModel.state.value.products.size)
        assertEquals("Peas", viewModel.state.value.products.first().name)
    }

    @Test
    fun increment_and_decrement_update_quantity_in_place() = runTest {
        val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
        val viewModel = ProductsViewModel(repo)
        viewModel.load(householdId = 1, shelfId = 1)

        viewModel.increment(1)
        assertEquals(3, viewModel.state.value.products.first().quantity)

        viewModel.decrement(1)
        assertEquals(2, viewModel.state.value.products.first().quantity)
    }

    @Test
    fun create_adds_a_product_at_zero_quantity() = runTest {
        val viewModel = ProductsViewModel(FakeProductRepository())
        viewModel.load(householdId = 1, shelfId = 1)
        viewModel.onNewNameChange("Bread")

        viewModel.create()

        assertTrue(viewModel.state.value.products.any { it.name == "Bread" && it.quantity == 0 })
        assertEquals("", viewModel.state.value.newName)
    }

    @Test
    fun list_failure_surfaces_an_error() = runTest {
        val repo = FakeProductRepository().apply { failList = true }
        val viewModel = ProductsViewModel(repo)

        viewModel.load(householdId = 1, shelfId = 1)

        assertEquals("offline", viewModel.state.value.error)
    }
}
