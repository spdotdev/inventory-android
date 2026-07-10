package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.missing.MissingItemsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MissingItemsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository(val households: List<HouseholdDto>) : HouseholdRepository {
        override fun getCached() = households
        override suspend fun list() = households
        override suspend fun create(name: String) = households.first()
        override suspend fun join(code: String) = households.first()
        override suspend fun leave(householdId: Long) {}
    }

    private class FakeLocationRepository(val byHousehold: Map<Long, List<LocationDto>> = emptyMap()) : LocationRepository {
        override fun getCached(householdId: Long) = byHousehold[householdId]
        override suspend fun list(householdId: Long) = byHousehold[householdId].orEmpty()
        override suspend fun create(householdId: Long, name: String, type: String) = LocationDto(99, name, type)
        override suspend fun delete(householdId: Long, locationId: Long) {}
    }

    private class FakeShelfRepository(val byLocation: Map<Long, List<ShelfDto>> = emptyMap()) : ShelfRepository {
        override fun getCached(householdId: Long, locationId: Long) = byLocation[locationId]
        override suspend fun list(householdId: Long, locationId: Long) = byLocation[locationId].orEmpty()
        override suspend fun create(householdId: Long, locationId: Long, name: String) = ShelfDto(99, name, 0, locationId)
        override suspend fun delete(householdId: Long, locationId: Long, shelfId: Long) {}
    }

    private class FakeProductRepository(val byShelf: Map<Long, List<ProductDto>> = emptyMap()) : ProductRepository {
        override fun getCached(householdId: Long, shelfId: Long) = byShelf[shelfId]
        override suspend fun list(householdId: Long, shelfId: Long) = byShelf[shelfId].orEmpty()
        override suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int) = ProductDto(99, name, quantity, shelfId)
        override suspend fun update(householdId: Long, shelfId: Long, productId: Long, edit: ProductEdit) =
        ProductDto(productId, edit.name, 0, shelfId)
        override suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int) = byShelf[shelfId]!!.first { it.id == productId }
        override suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int) = byShelf[shelfId]!!.first { it.id == productId }
        override suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long) = byShelf[shelfId]!!.first { it.id == productId }
        override suspend fun delete(householdId: Long, shelfId: Long, productId: Long) {}
        override suspend fun uploadImage(householdId: Long, shelfId: Long, productId: Long, imageUri: Uri, mimeType: String) = byShelf[shelfId]!!.first { it.id == productId }
    }

    private fun viewModel(
        households: List<HouseholdDto> = listOf(HouseholdDto(1, "Home", "AAAA")),
        locationsByHousehold: Map<Long, List<LocationDto>> = emptyMap(),
        shelvesByLocation: Map<Long, List<ShelfDto>> = emptyMap(),
        productsByShelf: Map<Long, List<ProductDto>> = emptyMap(),
    ): MissingItemsViewModel {
        val store = HierarchyStore(
            FakeHouseholdRepository(households),
            FakeLocationRepository(locationsByHousehold),
            FakeShelfRepository(shelvesByLocation),
            FakeProductRepository(productsByShelf),
        )
        store.loadFromCache()
        return MissingItemsViewModel(store)
    }

    private class ThrowingHouseholdRepository : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null
        override suspend fun list(): List<HouseholdDto> = throw IOException("network down")
        override suspend fun create(name: String) = throw NotImplementedError()
        override suspend fun join(code: String) = throw NotImplementedError()
        override suspend fun leave(householdId: Long) {}
    }

    @Test
    fun load_failure_surfaces_error_and_no_items() = runTest {
        // W4: a failed load must expose error so the screen shows a retry rather
        // than the "all stocked" empty state — the worst miss for a warnings screen.
        val store = HierarchyStore(
            ThrowingHouseholdRepository(),
            FakeLocationRepository(),
            FakeShelfRepository(),
            FakeProductRepository(),
        )
        val vm = MissingItemsViewModel(store)

        store.refresh(userInitiated = true)

        val failed = vm.state.first { it.error != null }
        assertNotNull(failed.error)
        assertFalse(failed.loading)
        assertTrue(failed.items.isEmpty())
    }

    @Test
    fun empty_when_no_mandatory_products() = runTest {
        val vm = viewModel(
            locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
            shelvesByLocation = mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10))),
            productsByShelf = mapOf(100L to listOf(ProductDto(1, "Milk", 1, 100, is_mandatory = false))),
        )

        assertTrue(vm.state.value.items.isEmpty())
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun mandatory_product_with_zero_quantity_appears() = runTest {
        val vm = viewModel(
            locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
            shelvesByLocation = mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10))),
            productsByShelf = mapOf(100L to listOf(ProductDto(1, "Milk", 0, 100, is_mandatory = true))),
        )

        assertEquals(1, vm.state.value.items.size)
        assertEquals("Milk", vm.state.value.items.first().productName)
        assertEquals("Fridge", vm.state.value.items.first().locationName)
    }

    @Test
    fun mandatory_product_with_stock_does_not_appear() = runTest {
        val vm = viewModel(
            locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
            shelvesByLocation = mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10))),
            productsByShelf = mapOf(100L to listOf(ProductDto(1, "Milk", 3, 100, is_mandatory = true))),
        )

        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun items_are_sorted_by_location_then_shelf_then_name() = runTest {
        val vm = viewModel(
            locationsByHousehold = mapOf(1L to listOf(
                LocationDto(10, "Pantry", "pantry"),
                LocationDto(11, "Fridge", "fridge"),
            )),
            shelvesByLocation = mapOf(
                10L to listOf(ShelfDto(100, "Top", 0, 10)),
                11L to listOf(ShelfDto(101, "Middle", 0, 11)),
            ),
            productsByShelf = mapOf(
                100L to listOf(ProductDto(1, "Bread", 0, 100, is_mandatory = true)),
                101L to listOf(ProductDto(2, "Milk", 0, 101, is_mandatory = true)),
            ),
        )

        val names = vm.state.value.items.map { it.locationName }
        assertEquals(listOf("Fridge", "Pantry"), names)
    }
}
