package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.products.ProductsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeProductRepository : ProductRepository {
        val items = mutableListOf<ProductDto>()
        var failList = false
        var lastMove: Triple<Long, Long, Long>? = null

        override fun getCached(householdId: Long, shelfId: Long): List<ProductDto>? = null

        override suspend fun list(householdId: Long, shelfId: Long): List<ProductDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int): ProductDto {
            val dto = ProductDto(id = (items.size + 1).toLong(), name = name, quantity = quantity, shelf_id = shelfId)
            items.add(dto)
            return dto
        }

        override suspend fun update(householdId: Long, shelfId: Long, productId: Long, name: String, description: String?, code: String?, isMandatory: Boolean): ProductDto {
            val index = items.indexOfFirst { it.id == productId }
            val updated = items[index].copy(name = name, description = description, code = code, is_mandatory = isMandatory)
            items[index] = updated
            return updated
        }

        override suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
            adjust(productId, amount)

        override suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
            adjust(productId, -amount)

        override suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long): ProductDto {
            lastMove = Triple(shelfId, productId, targetShelfId)
            return items.first { it.id == productId }
        }

        override suspend fun delete(householdId: Long, shelfId: Long, productId: Long) {
            items.removeIf { it.id == productId }
        }

        override suspend fun uploadImage(householdId: Long, shelfId: Long, productId: Long, imageUri: Uri, mimeType: String): ProductDto =
            items.first { it.id == productId }

        private fun adjust(productId: Long, delta: Int): ProductDto {
            val index = items.indexOfFirst { it.id == productId }
            val updated = items[index].let { it.copy(quantity = (it.quantity + delta).coerceAtLeast(0)) }
            items[index] = updated
            return updated
        }
    }

    private class FakeLocationRepository(private val locations: List<LocationDto>) : LocationRepository {
        override fun getCached(householdId: Long): List<LocationDto>? = null
        override suspend fun list(householdId: Long): List<LocationDto> = locations
        override suspend fun create(householdId: Long, name: String, type: String): LocationDto =
            LocationDto(99, name, type)
        override suspend fun delete(householdId: Long, locationId: Long) {}
    }

    private class FakeShelfRepository(private val byLocation: Map<Long, List<ShelfDto>>) : ShelfRepository {
        override fun getCached(householdId: Long, locationId: Long): List<ShelfDto>? = null
        override suspend fun list(householdId: Long, locationId: Long): List<ShelfDto> = byLocation[locationId].orEmpty()
        override suspend fun create(householdId: Long, locationId: Long, name: String): ShelfDto =
            ShelfDto(99, name, 0, locationId)
        override suspend fun delete(householdId: Long, locationId: Long, shelfId: Long) {}
    }

    private class FakeSearchRepository : SearchRepository {
        override suspend fun search(householdId: Long, query: String): List<SearchResultDto> = emptyList()
    }

    private class FakeHouseholdRepository : HouseholdRepository {
        override fun getCached() = emptyList<HouseholdDto>()
        override suspend fun list() = emptyList<HouseholdDto>()
        override suspend fun create(name: String) = HouseholdDto(1, name, "")
        override suspend fun join(code: String) = HouseholdDto(1, "", code)
        override suspend fun leave(householdId: Long) {}
    }

    private fun viewModel(
        products: FakeProductRepository = FakeProductRepository(),
        locations: FakeLocationRepository = FakeLocationRepository(emptyList()),
        shelves: FakeShelfRepository = FakeShelfRepository(emptyMap()),
        search: FakeSearchRepository = FakeSearchRepository(),
    ): ProductsViewModel {
        val store = HierarchyStore(FakeHouseholdRepository(), locations, shelves, products)
        return ProductsViewModel(products, locations, shelves, search, store)
    }

    @Test
    fun load_populates_products() = runTest {
        val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
        val vm = viewModel(products = repo)

        vm.load(householdId = 1, shelfId = 1)

        assertEquals(1, vm.state.value.products.size)
        assertEquals("Peas", vm.state.value.products.first().name)
    }

    @Test
    fun increment_and_decrement_update_quantity_in_place() = runTest {
        val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
        val vm = viewModel(products = repo)
        vm.load(householdId = 1, shelfId = 1)

        vm.increment(1)
        assertEquals(3, vm.state.value.products.first().quantity)
        vm.decrement(1)
        assertEquals(2, vm.state.value.products.first().quantity)
    }

    @Test
    fun create_adds_a_product_at_zero_quantity() = runTest {
        val vm = viewModel()
        vm.load(householdId = 1, shelfId = 1)
        vm.onNewNameChange("Bread")

        vm.create()

        assertTrue(vm.state.value.products.any { it.name == "Bread" && it.quantity == 0 })
        assertEquals("", vm.state.value.newName)
    }

    @Test
    fun start_move_lists_other_shelves_and_confirm_moves_the_product() = runTest {
        val products = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
        val locations = FakeLocationRepository(listOf(LocationDto(10, "Chest", "freezer")))
        // Shelves under location 10: current shelf 1 + a target shelf 2.
        val shelves = FakeShelfRepository(mapOf(10L to listOf(ShelfDto(1, "Top", 0, 10L), ShelfDto(2, "Bottom", 1, 10L))))
        val vm = viewModel(products, locations, shelves)
        vm.load(householdId = 1, shelfId = 1)

        vm.startMove(productId = 1)
        val targets = vm.state.value.moveTargets
        assertEquals(1, targets.size) // current shelf (1) excluded, only shelf 2 remains
        assertEquals("Chest › Bottom", targets.first().label)

        vm.confirmMove(targetShelfId = targets.first().shelfId)

        assertEquals(Triple(1L, 1L, 2L), products.lastMove)
        assertTrue(vm.state.value.products.isEmpty()) // moved off this shelf
        assertEquals(null, vm.state.value.movingProductId)
    }

    @Test
    fun update_replaces_product_in_list() = runTest {
        val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Milk", 2, 1)) }
        val vm = viewModel(products = repo)
        vm.load(householdId = 1, shelfId = 1)

        vm.update(productId = 1, name = "Oat Milk", description = "lactose free", code = null, isMandatory = true)

        val product = vm.state.value.products.first { it.id == 1L }
        assertEquals("Oat Milk", product.name)
        assertTrue(product.is_mandatory == true)
    }

    @Test
    fun delete_optimistically_removes_product() = runTest {
        val repo = FakeProductRepository().apply {
            items.add(ProductDto(1, "Milk", 2, 1))
            items.add(ProductDto(2, "Butter", 1, 1))
        }
        val vm = viewModel(products = repo)
        vm.load(householdId = 1, shelfId = 1)
        assertEquals(2, vm.state.value.products.size)

        vm.delete(productId = 1)

        assertEquals(1, vm.state.value.products.size)
        assertEquals("Butter", vm.state.value.products.first().name)
    }

    @Test
    fun cancel_move_clears_move_state() = runTest {
        val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
        val locations = FakeLocationRepository(listOf(LocationDto(10, "Chest", "freezer")))
        val shelves = FakeShelfRepository(mapOf(10L to listOf(ShelfDto(1, "Top", 0, 10L), ShelfDto(2, "Bottom", 1, 10L))))
        val vm = viewModel(repo, locations, shelves)
        vm.load(householdId = 1, shelfId = 1)
        vm.startMove(productId = 1)
        assertTrue(vm.state.value.movingProductId != null)

        vm.cancelMove()

        assertEquals(null, vm.state.value.movingProductId)
        assertTrue(vm.state.value.moveTargets.isEmpty())
    }

    @Test
    fun list_failure_surfaces_an_error() = runTest {
        val repo = FakeProductRepository().apply { failList = true }
        val vm = viewModel(products = repo)

        vm.load(householdId = 1, shelfId = 1)

        assertEquals("offline", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
