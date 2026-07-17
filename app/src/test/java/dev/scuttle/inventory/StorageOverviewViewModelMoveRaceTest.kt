package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.ui.storage.StorageOverviewViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Split out of StorageOverviewViewModelTest (detekt LargeClass, same reason
 * StorageOverviewViewModelHierarchyRefreshTest was split out) — covers the
 * IMPORTANT double-tap lost-update race fix: (a) a second move() while one is
 * still in flight is now dropped rather than launching a second coroutine
 * that could interleave with the first's, and (b) a failed move re-syncs from
 * the server instead of blindly restoring the `preMove` snapshot captured at
 * that call's start. Mirrors ShelvesViewModelMoveRaceTest one level up (no
 * system-shelf equivalent at the location level). Uses its own minimal fake
 * rather than the one in StorageOverviewViewModelTest, matching that split's
 * own precedent.
 */
class StorageOverviewViewModelMoveRaceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeLocationRepository : LocationRepository {
        val items = mutableListOf<LocationDto>()
        var reorderCalls = 0
        var lastReorderIds: List<Long>? = null
        var reorderGate: CompletableDeferred<Unit>? = null
        var failReorder = false

        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long): List<LocationDto> = items.toList()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ): LocationDto = throw NotImplementedError()

        override suspend fun reorder(
            householdId: Long,
            ids: List<Long>,
        ): List<LocationDto> {
            reorderGate?.await()
            reorderCalls++
            if (failReorder) throw IOException("reorder failed")
            lastReorderIds = ids
            ids.forEachIndexed { i, id ->
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) items[index] = items[index].copy(position = i)
            }
            return items.toList()
        }
    }

    private class FakeRestoreRepository : RestoreRepository {
        override suspend fun restore(
            householdId: Long,
            batchId: String,
        ): Int = 1
    }

    private class FakeHouseholdRepository : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = emptyList()

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = Unit
    }

    private fun viewModel(repo: FakeLocationRepository): StorageOverviewViewModel =
        StorageOverviewViewModel(
            repo,
            TestHierarchy.store(FakeHouseholdRepository()),
            FakeRestoreRepository(),
        )

    @Test
    fun a_second_move_while_one_is_in_flight_is_dropped() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Chest", "freezer", 0))
                    items.add(LocationDto(2, "Fridge", "fridge", 1))
                    items.add(LocationDto(3, "Pantry", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            repo.reorderGate = CompletableDeferred()

            vm.moveUp(3L) // parks in flight: optimistic order is Chest, Pantry, Fridge
            // A rapid second tap while the first is still in flight — must be
            // dropped entirely, not launch a second coroutine.
            vm.moveDown(1L)

            repo.reorderGate?.complete(Unit)

            // Only the FIRST move's request ever reached the repository.
            assertEquals(1, repo.reorderCalls)
            assertEquals(listOf(1L, 3L, 2L), repo.lastReorderIds)
            assertEquals(
                listOf("Chest", "Pantry", "Fridge"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun a_failed_move_resyncs_from_the_server_instead_of_blindly_restoring_the_pre_move_snapshot() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Chest", "freezer", 0))
                    items.add(LocationDto(2, "Fridge", "fridge", 1))
                    items.add(LocationDto(3, "Pantry", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            repo.reorderGate = CompletableDeferred()

            vm.moveUp(3L) // optimistic: Chest, Pantry, Fridge

            // While the reorder is still in flight, the server's truth changes
            // through a path this screen never observes directly (another device
            // renamed a location) — the resync path (repository.list()) will
            // pick this up; a blind restore of the `preMove` snapshot captured
            // at gesture-start could not, since that snapshot predates the rename.
            repo.items[1] = repo.items[1].copy(name = "Fridge (renamed elsewhere)")
            repo.failReorder = true
            repo.reorderGate?.complete(Unit)

            assertTrue(
                vm.state.value.locations
                    .map { it.name }
                    .contains("Fridge (renamed elsewhere)"),
            )
        }
}
