package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.settings.ShelfViewStore
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Split out of ShelvesViewModelTest (detekt LargeClass, same reason
 * StorageOverviewViewModelHierarchyRefreshTest was split out) — covers the
 * IMPORTANT double-tap lost-update race fix: (a) a second move() while one is
 * still in flight is now dropped rather than launching a second coroutine
 * that could interleave with the first's, and (b) a failed move re-syncs from
 * the server instead of blindly restoring the `preMove` snapshot captured at
 * that call's start. Uses its own minimal fake rather than the one in
 * ShelvesViewModelTest, matching that split's own precedent.
 */
class ShelvesViewModelMoveRaceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeShelfRepository : ShelfRepository {
        val items = mutableListOf<ShelfDto>()
        var reorderCalls = 0
        var lastReorderIds: List<Long>? = null
        var reorderGate: CompletableDeferred<Unit>? = null
        var failReorder = false

        override fun getCached(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto>? = null

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto> = items.toList()

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ): ShelfDto = throw NotImplementedError()

        override suspend fun reorder(
            householdId: Long,
            locationId: Long,
            ids: List<Long>,
        ): List<ShelfDto> {
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

    private class FakeShelfViewStore : ShelfViewStore {
        override fun isListView() = true

        override fun setListView(value: Boolean) {}
    }

    private fun viewModel(repo: FakeShelfRepository): ShelvesViewModel =
        ShelvesViewModel(
            repo,
            FakeRestoreRepository(),
            FakeShelfViewStore(),
            TestHierarchy.store(FakeHouseholdRepositoryForStore()),
        )

    /** Empty on purpose: this test file never needs a real household list. */
    private class FakeHouseholdRepositoryForStore : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = emptyList()

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = Unit
    }

    @Test
    fun a_second_move_while_one_is_in_flight_is_dropped() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            repo.reorderGate = CompletableDeferred()

            vm.moveUp(3L) // parks in flight: optimistic order is Top, Bottom, Middle
            // A rapid second tap while the first is still in flight — must be
            // dropped entirely, not launch a second coroutine.
            vm.moveDown(1L)

            repo.reorderGate?.complete(Unit)

            // Only the FIRST move's request ever reached the repository.
            assertEquals(1, repo.reorderCalls)
            assertEquals(listOf(1L, 3L, 2L), repo.lastReorderIds)
            assertEquals(
                listOf("Top", "Bottom", "Middle"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun a_failed_move_resyncs_from_the_server_instead_of_blindly_restoring_the_pre_move_snapshot() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            repo.reorderGate = CompletableDeferred()

            vm.moveUp(3L) // optimistic: Top, Bottom, Middle

            // While the reorder is still in flight, the server's truth changes
            // through a path this screen never observes directly (another device
            // renamed a shelf) — the resync path (repository.list()) will pick
            // this up; a blind restore of the `preMove` snapshot captured at
            // gesture-start could not, since that snapshot predates the rename.
            repo.items[1] = repo.items[1].copy(name = "Middle (renamed elsewhere)")
            repo.failReorder = true
            repo.reorderGate?.complete(Unit)

            assertTrue(
                vm.state.value.shelves
                    .map { it.name }
                    .contains("Middle (renamed elsewhere)"),
            )
        }
}
