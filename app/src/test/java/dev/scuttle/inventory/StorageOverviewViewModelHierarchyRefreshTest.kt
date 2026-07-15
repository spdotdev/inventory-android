package dev.scuttle.inventory

import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.LocationDeletion
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.ui.storage.StorageOverviewViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Split out of StorageOverviewViewModelTest (final review, detekt LargeClass) —
 * these are the `hierarchyStore.refresh()` coverage tests (Minor 3, Task 5/5b
 * review). Mutating any of the underlying `hierarchyStore.refresh()` call sites
 * to `Unit` left the whole suite green before this: a deleted/created/renamed/
 * reordered/undone location would silently keep rendering on Home/the drawer
 * until a manual pull-to-refresh. Each test below waits on HierarchyStore's own
 * `state` (built from a real HierarchyStore, not a fake) for the numbered
 * household [CountingHouseholdRepository.list] mints, so a test can only pass
 * once the SPECIFIC `hierarchyStore.refresh()` call site under test has
 * actually fired and its result has landed.
 */
class StorageOverviewViewModelHierarchyRefreshTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** A minimal, faithful fake — only what load()/create()/rename()/reorder()/deleteWithStrategy() need. */
    private class FakeLocationRepository : LocationRepository {
        val items = mutableListOf<LocationDto>()

        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long): List<LocationDto> = items.toList()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ): LocationDto {
            val dto = LocationDto(id = (items.size + 1).toLong(), name = name, type = type)
            items.add(dto)
            return dto
        }

        override suspend fun rename(
            householdId: Long,
            locationId: Long,
            name: String,
            type: String,
        ): LocationDto {
            val index = items.indexOfFirst { it.id == locationId }
            val updated = items[index].copy(name = name, type = type)
            items[index] = updated
            return updated
        }

        override suspend fun reorder(
            householdId: Long,
            ids: List<Long>,
        ): List<LocationDto> {
            ids.forEachIndexed { i, id ->
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) items[index] = items[index].copy(position = i)
            }
            return items.toList()
        }

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            deletion: LocationDeletion,
        ) {
            items.removeIf { it.id == locationId }
        }
    }

    private class FakeRestoreRepository : RestoreRepository {
        override suspend fun restore(
            householdId: Long,
            batchId: String,
        ): Int = 1
    }

    /**
     * Each call to [list] is numbered, so a test can wait for a SPECIFIC call to
     * have landed in [HierarchyStore.state] — proving that exact
     * `hierarchyStore.refresh()` call site fired, rather than merely that SOME
     * earlier refresh happened to already leave `entries` non-empty.
     */
    private class CountingHouseholdRepository : HouseholdRepository {
        var listCalls = 0

        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> {
            listCalls++
            return listOf(HouseholdDto(1, "Home-$listCalls", "AAAA-1111"))
        }

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = Unit
    }

    private fun viewModel(
        repo: LocationRepository = FakeLocationRepository(),
        hierarchyStore: HierarchyStore,
        restoreRepository: RestoreRepository = FakeRestoreRepository(),
    ): StorageOverviewViewModel = StorageOverviewViewModel(repo, hierarchyStore, restoreRepository)

    @Test
    fun create_refreshes_the_hierarchy_store() =
        runTest {
            val householdRepo = CountingHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val vm = viewModel(hierarchyStore = store)
            vm.load(householdId = 1)
            vm.onNewNameChange("Pantry")

            vm.create()

            val refreshed = store.state.first { it.entries.any { e -> e.name == "Home-1" } }
            assertTrue(refreshed.entries.any { it.name == "Home-1" })
        }

    @Test
    fun rename_refreshes_the_hierarchy_store() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Freezr", "freezer", 0)) }
            val householdRepo = CountingHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val vm = viewModel(repo, hierarchyStore = store)
            vm.load(householdId = 1)

            vm.rename(1L, "Freezer", "freezer")

            val refreshed = store.state.first { it.entries.any { e -> e.name == "Home-1" } }
            assertTrue(refreshed.entries.any { it.name == "Home-1" })
        }

    @Test
    fun reordering_refreshes_the_hierarchy_store() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                }
            val householdRepo = CountingHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val vm = viewModel(repo, hierarchyStore = store)
            vm.load(householdId = 1)

            vm.moveDown(1L)

            val refreshed = store.state.first { it.entries.any { e -> e.name == "Home-1" } }
            assertTrue(refreshed.entries.any { it.name == "Home-1" })
        }

    @Test
    fun confirming_a_delete_refreshes_the_hierarchy_store() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val householdRepo = CountingHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val vm = viewModel(repo, hierarchyStore = store)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            val refreshed = store.state.first { it.entries.any { e -> e.name == "Home-1" } }
            assertTrue(refreshed.entries.any { it.name == "Home-1" })
        }

    @Test
    fun undo_delete_refreshes_the_hierarchy_store() =
        runTest {
            // Isolates undoDelete()'s OWN refresh call from confirmDelete()'s (which
            // also refreshes, on the same success path, just before this runs): the
            // counting repo mints a distinctly-named household per call, so this can
            // only pass once a SECOND refresh — undo's — has landed.
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val householdRepo = CountingHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val vm = viewModel(repo, hierarchyStore = store)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            // Absorb confirmDelete's own refresh (call #1) before testing undo's.
            store.state.first { it.entries.any { e -> e.name == "Home-1" } }

            vm.undoDelete()

            val refreshed = store.state.first { it.entries.any { e -> e.name == "Home-2" } }
            assertTrue(refreshed.entries.any { it.name == "Home-2" })
        }
}
