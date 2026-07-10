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
import dev.scuttle.inventory.ui.storage.StorageOverviewViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StorageOverviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeLocationRepository : LocationRepository {
        val items = mutableListOf<LocationDto>()
        var failList = false

        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long): List<LocationDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ): LocationDto {
            val dto = LocationDto(id = (items.size + 1).toLong(), name = name, type = type)
            items.add(dto)
            return dto
        }

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
        ) {
            items.removeIf { it.id == locationId }
        }
    }

    private fun emptyStore() =
        HierarchyStore(
            object : HouseholdRepository {
                override fun getCached() = emptyList<HouseholdDto>()

                override suspend fun list() = emptyList<HouseholdDto>()

                override suspend fun create(name: String) = HouseholdDto(1, name, "")

                override suspend fun join(code: String) = HouseholdDto(1, "", code)

                override suspend fun leave(householdId: Long) {}
            },
            object : LocationRepository {
                override fun getCached(householdId: Long) = emptyList<LocationDto>()

                override suspend fun list(householdId: Long) = emptyList<LocationDto>()

                override suspend fun create(
                    householdId: Long,
                    name: String,
                    type: String,
                ) = LocationDto(1, name, type)

                override suspend fun delete(
                    householdId: Long,
                    locationId: Long,
                ) {}
            },
            object : ShelfRepository {
                override fun getCached(
                    householdId: Long,
                    locationId: Long,
                ) = emptyList<ShelfDto>()

                override suspend fun list(
                    householdId: Long,
                    locationId: Long,
                ) = emptyList<ShelfDto>()

                override suspend fun create(
                    householdId: Long,
                    locationId: Long,
                    name: String,
                ) = ShelfDto(1, name, 0, locationId)

                override suspend fun delete(
                    householdId: Long,
                    locationId: Long,
                    shelfId: Long,
                ) {}
            },
            object : ProductRepository {
                override fun getCached(
                    householdId: Long,
                    shelfId: Long,
                ) = emptyList<ProductDto>()

                override suspend fun list(
                    householdId: Long,
                    shelfId: Long,
                ) = emptyList<ProductDto>()

                override suspend fun create(
                    householdId: Long,
                    shelfId: Long,
                    name: String,
                    quantity: Int,
                    code: String?,
                ) = ProductDto(
                    1,
                    name,
                    quantity,
                    shelfId,
                )

                override suspend fun update(
                    householdId: Long,
                    shelfId: Long,
                    productId: Long,
                    edit: ProductEdit,
                ) = ProductDto(productId, edit.name, 0, shelfId)

                override suspend fun add(
                    householdId: Long,
                    shelfId: Long,
                    productId: Long,
                    amount: Int,
                ) = ProductDto(productId, "", 0, shelfId)

                override suspend fun remove(
                    householdId: Long,
                    shelfId: Long,
                    productId: Long,
                    amount: Int,
                ) = ProductDto(
                    productId,
                    "",
                    0,
                    shelfId,
                )

                override suspend fun move(
                    householdId: Long,
                    shelfId: Long,
                    productId: Long,
                    targetShelfId: Long,
                ) = ProductDto(
                    productId,
                    "",
                    0,
                    targetShelfId,
                )

                override suspend fun delete(
                    householdId: Long,
                    shelfId: Long,
                    productId: Long,
                ) {}

                override suspend fun uploadImage(
                    householdId: Long,
                    shelfId: Long,
                    productId: Long,
                    imageUri: Uri,
                    mimeType: String,
                ) = ProductDto(
                    productId,
                    "",
                    0,
                    shelfId,
                )
            },
        )

    @Test
    fun load_populates_locations() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Chest", "freezer")) }
            val viewModel = StorageOverviewViewModel(repo, emptyStore())

            viewModel.load(householdId = 1)

            assertEquals(1, viewModel.state.value.locations.size)
            assertEquals("Chest", viewModel.state.value.locations.first().name)
        }

    @Test
    fun create_adds_a_location_with_the_selected_type() =
        runTest {
            val viewModel = StorageOverviewViewModel(FakeLocationRepository(), emptyStore())
            viewModel.load(householdId = 1)
            viewModel.onNewNameChange("Pantry")
            viewModel.onTypeSelect("pantry")

            viewModel.create()

            val locations = viewModel.state.value.locations
            assertTrue(locations.any { it.name == "Pantry" && it.type == "pantry" })
            assertEquals("", viewModel.state.value.newName)
        }

    @Test
    fun list_failure_surfaces_an_error() =
        runTest {
            val repo = FakeLocationRepository().apply { failList = true }
            val viewModel = StorageOverviewViewModel(repo, emptyStore())

            viewModel.load(householdId = 1)

            assertEquals("offline", viewModel.state.value.error)
        }

    /**
     * T9: the pull-to-refresh spinner (`refreshing`) must fire only on a user
     * refresh, not on mutations. A gated repo lets us inspect the in-flight state:
     * create() should show `loading` without `refreshing`, refresh() should show both.
     */
    @Test
    fun refresh_flags_the_pull_spinner_but_create_does_not() =
        runTest {
            val gate = GatedLocationRepository()
            val viewModel = StorageOverviewViewModel(gate, emptyStore())
            gate.release() // let the initial cache-miss load settle
            viewModel.load(householdId = 1)
            gate.reset()

            // A mutation is in flight: generic loading is on, the pull spinner is not.
            viewModel.onNewNameChange("Pantry")
            viewModel.create()
            assertTrue("mutation should set loading", viewModel.state.value.loading)
            assertFalse("mutation must NOT set the pull spinner", viewModel.state.value.refreshing)
            gate.release()
            assertFalse(viewModel.state.value.refreshing)

            // A user refresh is in flight: the pull spinner is on.
            gate.reset()
            viewModel.refresh()
            assertTrue("refresh should set the pull spinner", viewModel.state.value.refreshing)
            gate.release()
            assertFalse(viewModel.state.value.refreshing)
        }

    /** A LocationRepository whose suspend calls block on a gate until released. */
    private class GatedLocationRepository : LocationRepository {
        private var gate = kotlinx.coroutines.CompletableDeferred<Unit>()

        fun reset() {
            gate = kotlinx.coroutines.CompletableDeferred()
        }

        fun release() {
            if (!gate.isCompleted) gate.complete(Unit)
        }

        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long): List<LocationDto> {
            gate.await()
            return emptyList()
        }

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ): LocationDto {
            gate.await()
            return LocationDto(id = 1, name = name, type = type)
        }

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
        ) = gate.await()
    }
}
