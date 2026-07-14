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
import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {
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
        val byHousehold: Map<Long, List<LocationDto>>,
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
        val byLocation: Map<Long, List<ShelfDto>>,
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
        val byShelf: Map<Long, List<ProductDto>>,
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

    private class FakeFavoritesStore : FavoritesStore {
        private val locations = mutableSetOf<Long>()
        private val shelves = mutableSetOf<Long>()

        override fun getFavoriteLocations() = locations.toSet()

        override fun toggleFavoriteLocation(id: Long) {
            if (!locations.add(id)) locations.remove(id)
        }

        override fun isFavoriteLocation(id: Long) = id in locations

        override fun getFavoriteShelves() = shelves.toSet()

        override fun toggleFavoriteShelf(id: Long) {
            if (!shelves.add(id)) shelves.remove(id)
        }

        override fun isFavoriteShelf(id: Long) = id in shelves
    }

    private fun viewModel(
        households: List<HouseholdDto> = listOf(HouseholdDto(1, "Home", "AAAA")),
        locationsByHousehold: Map<Long, List<LocationDto>> = emptyMap(),
        shelvesByLocation: Map<Long, List<ShelfDto>> = emptyMap(),
        productsByShelf: Map<Long, List<ProductDto>> = emptyMap(),
        favoritesStore: FakeFavoritesStore = FakeFavoritesStore(),
    ): DashboardViewModel {
        val store =
            HierarchyStore(
                FakeHouseholdRepository(households),
                FakeLocationRepository(locationsByHousehold),
                FakeShelfRepository(shelvesByLocation),
                FakeProductRepository(productsByShelf),
            )
        store.loadFromCache()
        return DashboardViewModel(store, favoritesStore)
    }

    @Test
    fun aggregates_totals_correctly() =
        runTest {
            val vm =
                viewModel(
                    locationsByHousehold =
                        mapOf(
                            1L to
                                listOf(
                                    LocationDto(10, "Fridge", "fridge"),
                                    LocationDto(11, "Pantry", "pantry"),
                                ),
                        ),
                    shelvesByLocation =
                        mapOf(
                            10L to listOf(ShelfDto(100, "Top", 0, 10), ShelfDto(101, "Bottom", 1, 10)),
                            11L to listOf(ShelfDto(102, "Shelf", 0, 11)),
                        ),
                    productsByShelf =
                        mapOf(
                            100L to listOf(ProductDto(1, "Milk", 1, 100), ProductDto(2, "Butter", 2, 100)),
                            101L to listOf(ProductDto(3, "Eggs", 0, 101)),
                            102L to listOf(ProductDto(4, "Rice", 5, 102)),
                        ),
                )

            assertEquals(2, vm.state.value.totalLocations)
            assertEquals(3, vm.state.value.totalShelves)
            assertEquals(4, vm.state.value.totalProducts)
        }

    @Test
    fun counts_mandatory_warnings_when_stock_is_zero() =
        runTest {
            val vm =
                viewModel(
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                    shelvesByLocation = mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10))),
                    productsByShelf =
                        mapOf(
                            100L to
                                listOf(
                                    ProductDto(1, "Milk", 0, 100, is_mandatory = true),
                                    ProductDto(2, "Butter", 2, 100, is_mandatory = true),
                                    ProductDto(3, "Eggs", 0, 100, is_mandatory = false),
                                ),
                        ),
                )

            assertEquals(1, vm.state.value.mandatoryWarnings)
        }

    @Test
    fun has_no_households_flag_set_when_empty() =
        runTest {
            val vm = viewModel(households = emptyList())

            assertTrue(vm.state.value.hasNoHouseholds)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun toggle_favorite_location_updates_state() =
        runTest {
            val vm =
                viewModel(
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                    shelvesByLocation = mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10))),
                )

            vm.toggleFavoriteLocation(10L)
            assertTrue(10L in vm.state.value.favoriteLocationIds)

            vm.toggleFavoriteLocation(10L)
            assertFalse(10L in vm.state.value.favoriteLocationIds)
        }

    @Test
    fun toggle_favorite_shelf_updates_state() =
        runTest {
            val vm = viewModel()

            vm.toggleFavoriteShelf(100L)
            assertTrue(100L in vm.state.value.favoriteShelfIds)

            vm.toggleFavoriteShelf(100L)
            assertFalse(100L in vm.state.value.favoriteShelfIds)
        }

    @Test
    fun a_single_household_gets_no_attribution_ui() =
        runTest {
            val vm = viewModel(locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))

            // Issue #33 is a multi-household problem; a one-household user should see
            // the dashboard exactly as before — no headers, no badges, no caption.
            assertFalse(vm.state.value.showHouseholdAttribution)
            assertEquals(1, vm.state.value.households.size)
        }

    @Test
    fun two_households_are_exposed_with_their_theme_keys() =
        runTest {
            val vm =
                viewModel(
                    households =
                        listOf(
                            HouseholdDto(1, "Home", "AAAA", color = "teal", icon = "home"),
                            HouseholdDto(2, "Beach house", "BBBB"),
                        ),
                )

            assertTrue(vm.state.value.showHouseholdAttribution)
            val (home, beach) = vm.state.value.households
            assertEquals("Home", home.name)
            assertEquals("teal", home.color)
            assertEquals("home", home.icon)
            // Null keys are legal: the client derives a stable default from the id.
            assertEquals("Beach house", beach.name)
            assertEquals(null, beach.color)
        }

    @Test
    fun location_stats_are_grouped_per_household_in_store_order() =
        runTest {
            val vm =
                viewModel(
                    households =
                        listOf(
                            HouseholdDto(1, "Home", "AAAA"),
                            HouseholdDto(2, "Beach house", "BBBB"),
                        ),
                    locationsByHousehold =
                        mapOf(
                            1L to listOf(LocationDto(10, "Bijkeuken", "pantry"), LocationDto(11, "Kelder", "cellar")),
                            2L to listOf(LocationDto(20, "Freezer Downstairs", "freezer")),
                        ),
                )

            val groups = vm.state.value.groupedLocationStats

            assertEquals(2, groups.size)
            assertEquals("Home", groups[0].household.name)
            assertEquals(listOf("Bijkeuken", "Kelder"), groups[0].stats.map { it.location.name })
            assertEquals("Beach house", groups[1].household.name)
            assertEquals(listOf("Freezer Downstairs"), groups[1].stats.map { it.location.name })
        }

    @Test
    fun a_household_with_no_locations_gets_no_group() =
        runTest {
            val vm =
                viewModel(
                    households =
                        listOf(
                            HouseholdDto(1, "Home", "AAAA"),
                            HouseholdDto(2, "Empty", "BBBB"),
                        ),
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Bijkeuken", "pantry"))),
                )

            assertEquals(
                listOf("Home"),
                vm.state.value.groupedLocationStats
                    .map { it.household.name },
            )
        }

    @Test
    fun the_bar_scale_is_global_so_households_stay_comparable() =
        runTest {
            val vm =
                viewModel(
                    households =
                        listOf(
                            HouseholdDto(1, "Home", "AAAA"),
                            HouseholdDto(2, "Beach house", "BBBB"),
                        ),
                    locationsByHousehold =
                        mapOf(
                            1L to listOf(LocationDto(10, "Bijkeuken", "pantry")),
                            2L to listOf(LocationDto(20, "Freezer", "freezer")),
                        ),
                    shelvesByLocation =
                        mapOf(
                            10L to listOf(ShelfDto(100, "Top", 0, 10)),
                            20L to listOf(ShelfDto(200, "Top", 0, 20)),
                        ),
                    productsByShelf =
                        mapOf(
                            100L to (1..9).map { ProductDto(it.toLong(), "P$it", 1, 100) },
                            200L to listOf(ProductDto(50, "Peas", 1, 200), ProductDto(51, "Fish", 1, 200)),
                        ),
                )

            // Scaled per-card, Beach house's 2 products would fill its bar as completely
            // as Home's 9 fill theirs, and the chart would lie about the comparison.
            assertEquals(9, vm.state.value.maxLocationProductCount)
        }

    @Test
    fun favorite_shelves_list_reflects_favorited_ids() =
        runTest {
            val vm =
                viewModel(
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                    shelvesByLocation =
                        mapOf(
                            10L to
                                listOf(
                                    ShelfDto(100, "Top", 0, 10),
                                    ShelfDto(101, "Bottom", 1, 10),
                                ),
                        ),
                )

            vm.toggleFavoriteShelf(100L)

            assertEquals(1, vm.state.value.favoriteShelves.size)
            assertEquals(
                "Top",
                vm.state.value.favoriteShelves
                    .first()
                    .shelf.name,
            )
        }
}
