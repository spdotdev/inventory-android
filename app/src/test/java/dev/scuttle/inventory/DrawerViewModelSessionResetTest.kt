package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.LocationDeletion
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.ui.app.DrawerViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Split out of DrawerViewModelTest (detekt LargeClass, same reason
 * StorageOverviewViewModelHierarchyRefreshTest was split out) — this is the
 * CRITICAL regression: DrawerViewModel is resolved once against the Activity's
 * ViewModelStoreOwner and survives a logout->login in the same process.
 * SessionCleaner.clear() only reaches Hilt @Singletons, not ViewModels, so
 * without the session-reset fix user B would see user A's phantom
 * delete-strategy dialog or "Deleted . Undo" snackbar carrying A's
 * household/location/batch ids, and confirming/undoing it would fire API calls
 * against A's ids under B's token. Uses its own minimal fakes rather than the
 * ones in DrawerViewModelTest, matching that split's own precedent.
 */
class DrawerViewModelSessionResetTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = emptyList()

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = Unit
    }

    /** Only what requestDelete()/confirmDelete() need. */
    private class FakeLocationRepository(
        private val byHousehold: Map<Long, List<LocationDto>>,
    ) : LocationRepository {
        val batchIdsUsed = mutableListOf<String>()

        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long) = byHousehold[householdId].orEmpty()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ) = throw NotImplementedError()

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            deletion: LocationDeletion,
        ) {
            batchIdsUsed += deletion.batchId
        }
    }

    private class FakeRestoreRepository : RestoreRepository {
        var restoreCalls = 0

        override suspend fun restore(
            householdId: Long,
            batchId: String,
        ): Int {
            restoreCalls++
            return 1
        }
    }

    @Test
    fun session_change_clears_pending_delete_and_undo_state() =
        runTest {
            val repo =
                FakeLocationRepository(
                    mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge", shelf_count = 0, product_count = 0))),
                )
            val restore = FakeRestoreRepository()
            val auth = TestHierarchy.FakeAuthRepository()
            val store = TestHierarchy.store(FakeHouseholdRepository())
            val vm = DrawerViewModel(store, repo, restore, auth)

            // User A opens the strategy dialog for one location.
            vm.requestDelete(householdId = 1, locationId = 10)
            assertTrue(vm.state.value.pendingDelete != null)

            // Session boundary: A logs out, B logs in — same process, same VM.
            auth.setActive(false)
            auth.setActive(true)

            assertNull(vm.state.value.pendingDelete)
            assertTrue(
                vm.state.value.moveTargets
                    .isEmpty(),
            )

            // The private pending household/location ids must also be gone, or
            // confirming now would delete A's location under B's token.
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            assertTrue(repo.batchIdsUsed.isEmpty())

            // User A (still, for this half of the test) completes a delete and gets
            // an Undo batch id back.
            vm.requestDelete(householdId = 1, locationId = 10)
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            assertEquals(1, repo.batchIdsUsed.size)
            assertTrue(vm.state.value.lastBatchId != null)

            // Another session boundary.
            auth.setActive(false)
            auth.setActive(true)

            assertNull(vm.state.value.lastBatchId)
            assertNull(vm.state.value.undoResult)

            // Undo must now be a no-op — it would otherwise replay A's batch/
            // household against B's token.
            vm.undoDelete()
            assertEquals(0, restore.restoreCalls)
        }
}
