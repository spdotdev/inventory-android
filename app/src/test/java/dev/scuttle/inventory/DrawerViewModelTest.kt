package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.app.DrawerViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DrawerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository(val households: List<HouseholdDto>) : HouseholdRepository {
        override fun getCached() = null
        override suspend fun list() = households
        override suspend fun create(name: String) = households.first()
        override suspend fun join(code: String) = households.first()
        override suspend fun leave(householdId: Long) {}
    }

    private class FakeLocationRepository(val byHousehold: Map<Long, List<LocationDto>> = emptyMap()) : LocationRepository {
        override fun getCached(householdId: Long) = null
        override suspend fun list(householdId: Long) = byHousehold[householdId].orEmpty()
        override suspend fun create(householdId: Long, name: String, type: String) = LocationDto(99, name, type)
        override suspend fun delete(householdId: Long, locationId: Long) {
            (byHousehold[householdId] as? MutableList)?.removeIf { it.id == locationId }
        }
    }

    private class FakeShelfRepository(val byLocation: Map<Long, List<ShelfDto>> = emptyMap()) : ShelfRepository {
        override fun getCached(householdId: Long, locationId: Long) = null
        override suspend fun list(householdId: Long, locationId: Long) = byLocation[locationId].orEmpty()
        override suspend fun create(householdId: Long, locationId: Long, name: String) = ShelfDto(99, name, 0, locationId)
        override suspend fun delete(householdId: Long, locationId: Long, shelfId: Long) {}
    }

    private class FakeProductRepository(val byShelf: Map<Long, List<ProductDto>> = emptyMap()) : ProductRepository {
        override fun getCached(householdId: Long, shelfId: Long) = null
        override suspend fun list(householdId: Long, shelfId: Long) = byShelf[shelfId].orEmpty()
        override suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int) = ProductDto(99, name, quantity, shelfId)
        override suspend fun update(householdId: Long, shelfId: Long, productId: Long, name: String, description: String?, code: String?, isMandatory: Boolean) = ProductDto(productId, name, 0, shelfId)
        override suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int) = byShelf[shelfId]!!.first { it.id == productId }
        override suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int) = byShelf[shelfId]!!.first { it.id == productId }
        override suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long) = byShelf[shelfId]!!.first { it.id == productId }
        override suspend fun delete(householdId: Long, shelfId: Long, productId: Long) {}
    }

    @Test
    fun refresh_populates_entries_with_locations() = runTest {
        val vm = DrawerViewModel(
            FakeHouseholdRepository(listOf(HouseholdDto(1, "Home", "AAAA"))),
            FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))),
            FakeShelfRepository(),
            FakeProductRepository(),
        )

        vm.refresh()

        assertEquals(1, vm.state.value.entries.size)
        assertEquals("Home", vm.state.value.entries.first().name)
        assertEquals(1, vm.state.value.entries.first().locations.size)
    }

    @Test
    fun refresh_counts_missing_mandatory_items() = runTest {
        val vm = DrawerViewModel(
            FakeHouseholdRepository(listOf(HouseholdDto(1, "Home", "AAAA"))),
            FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))),
            FakeShelfRepository(mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10)))),
            FakeProductRepository(mapOf(100L to listOf(
                ProductDto(1, "Milk", 0, 100, is_mandatory = true),
                ProductDto(2, "Butter", 1, 100, is_mandatory = true),
            ))),
        )

        vm.refresh()

        assertEquals(1, vm.state.value.missingItemCount)
    }

    @Test
    fun report_location_warning_updates_map() = runTest {
        val vm = DrawerViewModel(
            FakeHouseholdRepository(emptyList()),
            FakeLocationRepository(),
            FakeShelfRepository(),
            FakeProductRepository(),
        )

        vm.reportLocationWarning(locationId = 10, hasWarning = true)
        assertTrue(vm.state.value.locationWarnings[10] == true)

        vm.reportLocationWarning(locationId = 10, hasWarning = false)
        assertTrue(vm.state.value.locationWarnings[10] == false)
    }

    @Test
    fun delete_location_triggers_refresh() = runTest {
        val locations = mutableListOf(LocationDto(10, "Fridge", "fridge"), LocationDto(11, "Pantry", "pantry"))
        val locationRepo = object : LocationRepository {
            override fun getCached(householdId: Long) = null
            override suspend fun list(householdId: Long) = locations.toList()
            override suspend fun create(householdId: Long, name: String, type: String) = LocationDto(99, name, type)
            override suspend fun delete(householdId: Long, locationId: Long) { locations.removeIf { it.id == locationId } }
        }
        val vm = DrawerViewModel(
            FakeHouseholdRepository(listOf(HouseholdDto(1, "Home", "AAAA"))),
            locationRepo,
            FakeShelfRepository(),
            FakeProductRepository(),
        )
        vm.refresh()
        assertEquals(2, vm.state.value.entries.first().locations.size)

        vm.deleteLocation(householdId = 1, locationId = 10)

        assertEquals(1, vm.state.value.entries.first().locations.size)
        assertEquals("Pantry", vm.state.value.entries.first().locations.first().name)
    }
}
