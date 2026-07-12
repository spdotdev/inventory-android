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
import dev.scuttle.inventory.ui.app.DrawerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class DrawerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository(
        val households: List<HouseholdDto>,
    ) : HouseholdRepository {
        override fun getCached() = households

        override suspend fun list() = households

        override suspend fun create(name: String) = households.first()

        override suspend fun join(code: String) = households.first()

        override suspend fun leave(householdId: Long) {}
    }

    private class FakeLocationRepository(
        private val initial: Map<Long, List<LocationDto>> = emptyMap(),
    ) : LocationRepository {
        val data = initial.mapValues { it.value.toMutableList() }.toMutableMap()

        override fun getCached(householdId: Long) = data[householdId]

        override suspend fun list(householdId: Long) = data[householdId].orEmpty()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ) = LocationDto(99, name, type)

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
        ) {
            data[householdId]?.removeIf { it.id == locationId }
        }
    }

    private class FakeShelfRepository(
        val byLocation: Map<Long, List<ShelfDto>> = emptyMap(),
    ) : ShelfRepository {
        override fun getCached(
            householdId: Long,
            locationId: Long,
        ) = byLocation[locationId]

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ) = byLocation[locationId].orEmpty()

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ) = ShelfDto(99, name, 0, locationId)

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
        ) {}
    }

    private class FakeProductRepository(
        val byShelf: Map<Long, List<ProductDto>> = emptyMap(),
    ) : ProductRepository {
        override fun getCached(
            householdId: Long,
            shelfId: Long,
        ) = byShelf[shelfId]

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ) = byShelf[shelfId].orEmpty()

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            name: String,
            quantity: Int,
            code: String?,
        ) = ProductDto(99, name, quantity, shelfId)

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
        ) = byShelf[shelfId]!!.first { it.id == productId }

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = byShelf[shelfId]!!.first {
            it.id == productId
        }

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            targetShelfId: Long,
        ) = byShelf[shelfId]!!.first {
            it.id == productId
        }

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
        ) = byShelf[shelfId]!!.first {
            it.id == productId
        }
    }

    private fun makeStore(
        households: List<HouseholdDto>,
        locationsByHousehold: Map<Long, List<LocationDto>> = emptyMap(),
        shelvesByLocation: Map<Long, List<ShelfDto>> = emptyMap(),
        productsByShelf: Map<Long, List<ProductDto>> = emptyMap(),
        locationRepo: LocationRepository? = null,
    ): Pair<HierarchyStore, LocationRepository> {
        val locRepo = locationRepo ?: FakeLocationRepository(locationsByHousehold)
        val store =
            HierarchyStore(
                FakeHouseholdRepository(households),
                locRepo,
                FakeShelfRepository(shelvesByLocation),
                FakeProductRepository(productsByShelf),
            )
        store.loadFromCache()
        return store to locRepo
    }

    @Test
    fun refresh_populates_entries_with_locations() =
        runTest {
            val (store, locRepo) =
                makeStore(
                    households = listOf(HouseholdDto(1, "Home", "AAAA")),
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                )
            val vm = DrawerViewModel(store, locRepo)

            assertEquals(1, vm.state.value.entries.size)
            assertEquals(
                "Home",
                vm.state.value.entries
                    .first()
                    .name,
            )
            assertEquals(
                1,
                vm.state.value.entries
                    .first()
                    .locations.size,
            )
        }

    @Test
    fun refresh_counts_missing_mandatory_items() =
        runTest {
            val (store, locRepo) =
                makeStore(
                    households = listOf(HouseholdDto(1, "Home", "AAAA")),
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                    shelvesByLocation = mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10))),
                    productsByShelf =
                        mapOf(
                            100L to
                                listOf(
                                    ProductDto(1, "Milk", 0, 100, is_mandatory = true),
                                    ProductDto(2, "Butter", 1, 100, is_mandatory = true),
                                ),
                        ),
                )
            val vm = DrawerViewModel(store, locRepo)

            assertEquals(1, vm.state.value.missingItemCount)
        }

    @Test
    fun report_location_warning_updates_map() =
        runTest {
            val (store, locRepo) = makeStore(households = emptyList())
            val vm = DrawerViewModel(store, locRepo)

            vm.reportLocationWarning(locationId = 10, hasWarning = true)
            assertTrue(vm.state.value.locationWarnings[10] == true)

            vm.reportLocationWarning(locationId = 10, hasWarning = false)
            assertTrue(vm.state.value.locationWarnings[10] == false)
        }

    private class ThrowingHouseholdRepository : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = throw IOException("network down")

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) {}
    }

    @Test
    fun refresh_failure_surfaces_error_and_clears_loading() =
        runTest {
            // W3: a failed load must reach DrawerUiState.error so AllStorages can show
            // a retry instead of the "No storages yet" empty state.
            val store =
                HierarchyStore(
                    ThrowingHouseholdRepository(),
                    FakeLocationRepository(),
                    FakeShelfRepository(),
                    FakeProductRepository(),
                )
            val vm = DrawerViewModel(store, FakeLocationRepository())

            store.refresh(userInitiated = true)

            val failed = vm.state.first { it.error != null }
            assertNotNull(failed.error)
            assertFalse(failed.loading)
            assertFalse(failed.refreshing)
            assertTrue(failed.entries.isEmpty())
        }

    @Test
    fun delete_location_failure_surfaces_action_error() =
        runTest {
            // W10: a failed swipe-to-delete must not be swallowed — it surfaces as a
            // one-shot actionError the screen shows as a snackbar.
            val throwingLocRepo =
                object : LocationRepository {
                    override fun getCached(householdId: Long): List<LocationDto>? = null

                    override suspend fun list(householdId: Long) = emptyList<LocationDto>()

                    override suspend fun create(
                        householdId: Long,
                        name: String,
                        type: String,
                    ) = throw NotImplementedError()

                    override suspend fun delete(
                        householdId: Long,
                        locationId: Long,
                    ) = throw IOException("delete failed")
                }
            val (store, _) = makeStore(households = emptyList())
            val vm = DrawerViewModel(store, throwingLocRepo)

            vm.deleteLocation(householdId = 1, locationId = 10)

            val message = vm.actionError.first { it != null }
            assertNotNull(message)
        }

    @Test
    fun delete_location_removes_it_from_entries() =
        runTest {
            val fakeLocRepo =
                FakeLocationRepository(
                    mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"), LocationDto(11, "Pantry", "pantry"))),
                )
            val (store, _) =
                makeStore(
                    households = listOf(HouseholdDto(1, "Home", "AAAA")),
                    locationRepo = fakeLocRepo,
                )
            val vm = DrawerViewModel(store, fakeLocRepo)
            assertEquals(
                2,
                vm.state.value.entries
                    .first()
                    .locations.size,
            )

            fakeLocRepo.data[1L]?.removeIf { it.id == 10L }
            store.loadFromCache()

            assertEquals(
                1,
                vm.state.value.entries
                    .first()
                    .locations.size,
            )
            assertEquals(
                "Pantry",
                vm.state.value.entries
                    .first()
                    .locations
                    .first()
                    .name,
            )
        }
}
