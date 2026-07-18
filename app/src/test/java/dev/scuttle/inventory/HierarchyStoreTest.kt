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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class HierarchyStoreTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository(
        private val households: List<HouseholdDto>,
        private val throwOnList: Boolean = false,
    ) : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> {
            if (throwOnList) throw IOException("network down")
            return households
        }

        override suspend fun create(name: String) = households.first()

        override suspend fun join(code: String) = households.first()

        override suspend fun leave(householdId: Long) {}
    }

    private class FakeLocationRepository(
        private val byHousehold: Map<Long, List<LocationDto>> = emptyMap(),
    ) : LocationRepository {
        override fun getCached(householdId: Long) = byHousehold[householdId]

        override suspend fun list(householdId: Long) = byHousehold[householdId].orEmpty()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ) = LocationDto(99, name, type)
    }

    private class FakeShelfRepository(
        private val byLocation: Map<Long, List<ShelfDto>> = emptyMap(),
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
    }

    private class FakeProductRepository(
        private val byShelf: Map<Long, List<ProductDto>> = emptyMap(),
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
        ) = "batch"

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

    @Test
    fun refresh_success_populates_state_and_clears_indicators() =
        runTest {
            val store =
                HierarchyStore(
                    FakeHouseholdRepository(
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    ),
                    FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))),
                    FakeShelfRepository(mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10)))),
                    FakeProductRepository(mapOf(100L to listOf(ProductDto(1, "Milk", 0, 100, is_mandatory = true)))),
                    UnconfinedTestDispatcher(),
                )

            store.refresh(userInitiated = true)

            val loaded = store.state.first { !it.loading }
            assertNull(loaded.errorRes)
            assertFalse(loaded.refreshing)
            assertEquals(1, loaded.entries.size)
            assertEquals(1, loaded.missingItemCount)
        }

    // AllStoragesScreen's per-location delete icon (Task 9) gates on
    // entry.canRestructure — this documents that HouseholdDto.can_restructure
    // survives the HouseholdDto -> HouseholdWithLocations mapping unmodified, per
    // household, so a Member in one household and Owner in another sees the
    // correct per-row gate even though the screen has one shared edit-mode
    // toggle for every household at once.
    @Test
    fun refresh_carries_can_restructure_per_household_into_entries() =
        runTest {
            val store =
                HierarchyStore(
                    FakeHouseholdRepository(
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                            HouseholdDto(
                                2,
                                "Friends' place",
                                "BBBB",
                                role = "member",
                                can_restructure = false,
                                can_manage_members = false,
                            ),
                        ),
                    ),
                    FakeLocationRepository(
                        mapOf(
                            1L to listOf(LocationDto(10, "Fridge", "fridge")),
                            2L to listOf(LocationDto(20, "Pantry", "pantry")),
                        ),
                    ),
                    FakeShelfRepository(),
                    FakeProductRepository(),
                    UnconfinedTestDispatcher(),
                )

            store.refresh(userInitiated = true)

            val loaded = store.state.first { !it.loading }
            assertTrue(loaded.entries.first { it.id == 1L }.canRestructure)
            assertFalse(loaded.entries.first { it.id == 2L }.canRestructure)
        }

    @Test
    fun refresh_failure_sets_error_and_clears_indicators() =
        runTest {
            // W16: a failed network refresh must set error and clear both loading and
            // refreshing so the UI can leave the spinner and show the failure.
            val store =
                HierarchyStore(
                    FakeHouseholdRepository(emptyList(), throwOnList = true),
                    FakeLocationRepository(),
                    FakeShelfRepository(),
                    FakeProductRepository(),
                    UnconfinedTestDispatcher(),
                )

            store.refresh(userInitiated = true)

            val failed = store.state.first { it.errorRes != null }
            assertNotNull(failed.errorRes)
            assertFalse(failed.loading)
            assertFalse(failed.refreshing)
        }

    @Test
    fun refresh_computes_location_warnings_without_any_pane_report() =
        runTest {
            // X2: the "⚠ needs attention" indicator must light up from server data on
            // load — a missing mandatory item on any shelf, not only the visible one.
            val store =
                HierarchyStore(
                    FakeHouseholdRepository(
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    ),
                    FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))),
                    FakeShelfRepository(
                        mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10), ShelfDto(101, "Bottom", 1, 10))),
                    ),
                    // The missing mandatory item sits on the SECOND (non-visible) shelf.
                    FakeProductRepository(mapOf(101L to listOf(ProductDto(1, "Milk", 0, 101, is_mandatory = true)))),
                    UnconfinedTestDispatcher(),
                )

            store.refresh(userInitiated = true)

            val loaded = store.state.first { !it.loading }
            assertEquals(true, loaded.locationWarnings[10])
        }

    @Test
    fun refresh_leaves_no_warning_when_all_mandatory_items_are_stocked() =
        runTest {
            val store =
                HierarchyStore(
                    FakeHouseholdRepository(
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    ),
                    FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))),
                    FakeShelfRepository(mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10)))),
                    FakeProductRepository(mapOf(100L to listOf(ProductDto(1, "Milk", 3, 100, is_mandatory = true)))),
                    UnconfinedTestDispatcher(),
                )

            store.refresh(userInitiated = true)

            val loaded = store.state.first { !it.loading }
            assertNull(loaded.locationWarnings[10])
        }

    @Test
    fun clear_resets_to_the_empty_state() =
        runTest {
            // X1: on session end the store must reset so one account's hierarchy never
            // renders to the next (loadFromCache would otherwise repopulate it).
            val store =
                HierarchyStore(
                    FakeHouseholdRepository(
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    ),
                    FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))),
                    FakeShelfRepository(mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10)))),
                    FakeProductRepository(mapOf(100L to listOf(ProductDto(1, "Milk", 0, 100, is_mandatory = true)))),
                    UnconfinedTestDispatcher(),
                )
            store.refresh(userInitiated = true)
            assertEquals(
                1,
                store.state
                    .first { !it.loading }
                    .entries.size,
            )

            store.clear()

            val cleared = store.state.value
            assertTrue(cleared.entries.isEmpty())
            assertEquals(0, cleared.missingItemCount)
            assertNull(cleared.errorRes)
        }

    @Test
    fun refresh_computes_low_stock_items_excluding_missing_ones() =
        runTest {
            val store =
                HierarchyStore(
                    FakeHouseholdRepository(
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    ),
                    FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))),
                    FakeShelfRepository(mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10)))),
                    FakeProductRepository(
                        mapOf(
                            100L to
                                listOf(
                                    // At/below threshold -> running low
                                    ProductDto(1, "Milk", 1, 100, low_stock_threshold = 2),
                                    // Above threshold -> fine
                                    ProductDto(2, "Eggs", 5, 100, low_stock_threshold = 2),
                                    // No threshold -> feature off
                                    ProductDto(3, "Peas", 0, 100),
                                    // Mandatory at 0 -> missing, NOT running low (one warning per item)
                                    ProductDto(4, "Butter", 0, 100, is_mandatory = true, low_stock_threshold = 3),
                                ),
                        ),
                    ),
                    UnconfinedTestDispatcher(),
                )

            store.refresh(userInitiated = true)

            val loaded = store.state.first { !it.loading }
            assertEquals(listOf("Milk"), loaded.lowStockItems.map { it.productName })
            assertEquals(1, loaded.lowStockItems.single().quantity)
            assertEquals(2, loaded.lowStockItems.single().threshold)
            assertEquals(1, loaded.missingItemCount)
        }
}
