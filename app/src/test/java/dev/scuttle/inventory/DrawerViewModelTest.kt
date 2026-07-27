package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.auth.AuthRepository
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
        private val cached: Map<Long, List<LocationDto>> = initial
        val live = initial.mapValues { it.value.toMutableList() }.toMutableMap()

        // Recorded for the createLocation() tests below.
        val createCallsByHousehold = mutableListOf<Triple<Long, String, String>>()
        var lastCreateColor: String? = null
        var lastCreateIcon: String? = null
        var failCreate = false

        override fun getCached(householdId: Long) = cached[householdId]

        override suspend fun list(householdId: Long) = live[householdId].orEmpty()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
            color: String?,
            icon: String?,
        ): LocationDto {
            createCallsByHousehold += Triple(householdId, name, type)
            lastCreateColor = color
            lastCreateIcon = icon
            if (failCreate) throw IOException("create failed")
            val created = LocationDto(99, name, type, color = color, icon = icon)
            live[householdId] = (live[householdId].orEmpty() + created).toMutableList()
            return created
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
                // A fresh, unconfined test dispatcher — not the production
                // Dispatchers.IO the 4-arg constructor falls back to.
                UnconfinedTestDispatcher(),
            )
        store.loadFromCache()
        return store to locRepo
    }

    private fun viewModel(
        store: HierarchyStore,
        locationRepo: LocationRepository,
        authRepository: AuthRepository = TestHierarchy.FakeAuthRepository(),
    ): DrawerViewModel = DrawerViewModel(store, locationRepo, authRepository)

    @Test
    fun refresh_populates_entries_with_locations() =
        runTest {
            val (store, locRepo) =
                makeStore(
                    households =
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
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                )
            val vm = viewModel(store, locRepo)

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
                    households =
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
            val vm = viewModel(store, locRepo)

            assertEquals(1, vm.state.value.missingItemCount)
        }

    @Test
    fun report_location_warning_updates_map() =
        runTest {
            val (store, locRepo) = makeStore(households = emptyList())
            val vm = viewModel(store, locRepo)

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
            // W3: a failed load must reach DrawerUiState.errorRes so AllStorages can show
            // a retry instead of the "No storages yet" empty state.
            val store =
                HierarchyStore(
                    ThrowingHouseholdRepository(),
                    FakeLocationRepository(),
                    FakeShelfRepository(),
                    FakeProductRepository(),
                    UnconfinedTestDispatcher(),
                )
            val vm = viewModel(store, FakeLocationRepository())

            store.refresh(userInitiated = true)

            val failed = vm.state.first { it.errorRes != null }
            assertNotNull(failed.errorRes)
            assertFalse(failed.loading)
            assertFalse(failed.refreshing)
            assertTrue(failed.entries.isEmpty())
        }

    // --- createLocation() — AllStorages' FAB add-storage sheet ---

    private val homeHousehold =
        HouseholdDto(1, "Home", "AAAA", role = "admin", can_restructure = true, can_manage_members = true)

    @Test
    fun create_location_calls_the_repository_with_the_household_name_and_type() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to emptyList()))
            val (store, _) = makeStore(households = listOf(homeHousehold), locationRepo = repo)
            val vm = viewModel(store, repo)

            vm.createLocation(householdId = 1, name = "Pantry", type = "pantry")

            assertEquals(listOf(Triple(1L, "Pantry", "pantry")), repo.createCallsByHousehold)
        }

    @Test
    fun create_location_refreshes_the_hierarchy_store_on_success() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to emptyList()))
            val (store, _) = makeStore(households = listOf(homeHousehold), locationRepo = repo)
            val vm = viewModel(store, repo)

            vm.createLocation(householdId = 1, name = "Pantry", type = "pantry")

            val refreshed =
                store.state.first {
                    it.entries
                        .first()
                        .locations
                        .any { l -> l.name == "Pantry" }
                }
            assertTrue(
                refreshed.entries
                    .first()
                    .locations
                    .any { it.name == "Pantry" },
            )
        }

    @Test
    fun create_location_surfaces_an_action_error_on_failure() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to emptyList())).apply { failCreate = true }
            val (store, _) = makeStore(households = listOf(homeHousehold), locationRepo = repo)
            val vm = viewModel(store, repo)

            vm.createLocation(householdId = 1, name = "Pantry", type = "pantry")

            val message = vm.actionErrorRes.first { it != null }
            assertNotNull(message)
        }

    @Test
    fun create_location_passes_the_selected_color_and_icon_to_the_repository() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to emptyList()))
            val (store, _) = makeStore(households = listOf(homeHousehold), locationRepo = repo)
            val vm = viewModel(store, repo)

            vm.createLocation(householdId = 1, name = "Pantry", type = "pantry", color = "teal", icon = "cottage")

            assertEquals("teal", repo.lastCreateColor)
            assertEquals("cottage", repo.lastCreateIcon)
        }

    @Test
    fun create_location_without_a_theme_passes_null_color_and_icon() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to emptyList()))
            val (store, _) = makeStore(households = listOf(homeHousehold), locationRepo = repo)
            val vm = viewModel(store, repo)

            vm.createLocation(householdId = 1, name = "Pantry", type = "pantry")

            assertNull(repo.lastCreateColor)
            assertNull(repo.lastCreateIcon)
        }

    @Test
    fun create_location_with_a_blank_name_does_nothing() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to emptyList()))
            val (store, _) = makeStore(households = listOf(homeHousehold), locationRepo = repo)
            val vm = viewModel(store, repo)

            vm.createLocation(householdId = 1, name = "   ", type = "pantry")

            assertTrue(repo.createCallsByHousehold.isEmpty())
        }
}
