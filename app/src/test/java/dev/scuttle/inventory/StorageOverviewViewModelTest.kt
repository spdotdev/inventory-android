package dev.scuttle.inventory

import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.LocationDeletion
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.storage.StorageOverviewViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class StorageOverviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeLocationRepository : LocationRepository {
        val items = mutableListOf<LocationDto>()
        var failList = false

        // Recorded for assertions below — the real LocationRepository interface bundles
        // batchId/strategy/targetLocationId into one LocationDeletion (data/hierarchy/DeleteStrategy.kt),
        // so each call's deletion is captured whole and unpacked into these lists.
        val batchIdsUsed = mutableListOf<String>()
        val strategiesUsed = mutableListOf<LocationDeleteStrategy?>()
        val targetIdsUsed = mutableListOf<Long?>()
        val deletedLocationIds = mutableListOf<Long>()
        var lastRenamedId: Long? = null
        var lastReorderIds: List<Long>? = null

        // When set, deleteWithStrategy throws for this one id instead of deleting
        // it — simulates a mid-batch server failure.
        var failDeleteId: Long? = null

        var reorderGate: CompletableDeferred<Unit>? = null

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

        override suspend fun rename(
            householdId: Long,
            locationId: Long,
            name: String,
            type: String,
        ): LocationDto {
            lastRenamedId = locationId
            val index = items.indexOfFirst { it.id == locationId }
            val updated = items[index].copy(name = name, type = type)
            items[index] = updated
            return updated
        }

        // Simulates a rejected reorder (offline, or another member's concurrent
        // edit triggering a 422) — the request never lands server-side at all.
        var failReorder = false

        override suspend fun reorder(
            householdId: Long,
            ids: List<Long>,
        ): List<LocationDto> {
            reorderGate?.await()
            if (failReorder) throw IOException("reorder failed")
            lastReorderIds = ids
            // Only positions of the given ids change...
            ids.forEachIndexed { i, id ->
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) items[index] = items[index].copy(position = i)
            }
            // ...but the FULL location list for the household is returned:
            // PATCH .../locations/reorder ends with `return $this->index(...)`
            // server-side, same shape as the shelves endpoint. A fake that
            // returned only the reordered subset would hide the exact bug this
            // fake exists to catch (the VM merging its own local pre-reorder
            // copy on top of the one the server already sent back — a
            // duplicate id that crashes a LazyColumn keyed by location.id).
            return items.toList()
        }

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            deletion: LocationDeletion,
        ) {
            if (locationId == failDeleteId) throw RuntimeException("delete failed")
            batchIdsUsed += deletion.batchId
            strategiesUsed += deletion.strategy
            targetIdsUsed += deletion.targetLocationId
            deletedLocationIds += locationId
            items.removeIf { it.id == locationId }
        }
    }

    private class FakeRestoreRepository : RestoreRepository {
        var lastHouseholdId: Long? = null
        var lastBatchId: String? = null
        var restoreCalls = 0

        // Simulates a 409 from the restore endpoint (already restored elsewhere,
        // or permanently removed past the undo window).
        var fail = false

        override suspend fun restore(
            householdId: Long,
            batchId: String,
        ): Int {
            if (fail) throw IOException("409: already restored")
            lastHouseholdId = householdId
            lastBatchId = batchId
            restoreCalls++
            return 1
        }
    }

    private class FakeHouseholdRepository(
        private val households: List<HouseholdDto> = emptyList(),
    ) : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = households

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = Unit
    }

    private fun viewModel(
        repo: LocationRepository = FakeLocationRepository(),
        restoreRepository: RestoreRepository = FakeRestoreRepository(),
        hierarchyStore: HierarchyStore = TestHierarchy.store(FakeHouseholdRepository()),
    ): StorageOverviewViewModel = StorageOverviewViewModel(repo, hierarchyStore, restoreRepository)

    @Test
    fun load_populates_locations() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Chest", "freezer")) }
            val vm = viewModel(repo)

            vm.load(householdId = 1)

            assertEquals(1, vm.state.value.locations.size)
            assertEquals(
                "Chest",
                vm.state.value.locations
                    .first()
                    .name,
            )
        }

    @Test
    fun load_reflects_the_household_can_restructure_flag() =
        runTest {
            // The top-bar edit pencil (StorageOverviewScreen) gates on this — a Member
            // household (can_restructure = false) must never see it, since every
            // mutating LocationController route already 403s them server-side.
            val householdRepo =
                FakeHouseholdRepository(
                    listOf(
                        HouseholdDto(
                            1,
                            "Home",
                            "AAAA",
                            role = "member",
                            can_restructure = false,
                            can_manage_members = false,
                        ),
                    ),
                )
            val hierarchyStore = TestHierarchy.store(householdRepo)
            hierarchyStore.refresh(userInitiated = true)
            val vm = viewModel(hierarchyStore = hierarchyStore)

            vm.load(householdId = 1)

            assertFalse(vm.state.value.canRestructure)
        }

    @Test
    fun load_defaults_can_restructure_true_when_the_hierarchy_store_has_no_entry() =
        runTest {
            // A cold deep link straight into this screen, before the singleton
            // HierarchyStore has ever loaded this household — must not hide the
            // pencil the server would actually allow.
            val vm = viewModel()

            vm.load(householdId = 1)

            assertTrue(vm.state.value.canRestructure)
        }

    @Test
    fun create_adds_a_location_with_the_selected_type() =
        runTest {
            val vm = viewModel()
            vm.load(householdId = 1)
            vm.onNewNameChange("Pantry")
            vm.onTypeSelect("pantry")

            vm.create()

            val locations = vm.state.value.locations
            assertTrue(locations.any { it.name == "Pantry" && it.type == "pantry" })
            assertEquals("", vm.state.value.newName)
        }

    @Test
    fun list_failure_surfaces_an_error() =
        runTest {
            val repo = FakeLocationRepository().apply { failList = true }
            val vm = viewModel(repo)

            vm.load(householdId = 1)

            assertEquals("offline", vm.state.value.error)
        }

    /**
     * The pull-to-refresh spinner (`refreshing`) must fire only on a user
     * refresh, not on mutations. A gated repo lets us inspect the in-flight state:
     * create() should show `loading` without `refreshing`, refresh() should show both.
     */
    @Test
    fun refresh_flags_the_pull_spinner_but_create_does_not() =
        runTest {
            val gate = GatedLocationRepository()
            val vm = viewModel(gate)
            gate.release() // let the initial cache-miss load settle
            vm.load(householdId = 1)
            gate.reset()

            // A mutation is in flight: generic loading is on, the pull spinner is not.
            vm.onNewNameChange("Pantry")
            vm.create()
            assertTrue("mutation should set loading", vm.state.value.loading)
            assertFalse("mutation must NOT set the pull spinner", vm.state.value.refreshing)
            gate.release()
            assertFalse(vm.state.value.refreshing)

            // A user refresh is in flight: the pull spinner is on.
            gate.reset()
            vm.refresh()
            assertTrue("refresh should set the pull spinner", vm.state.value.refreshing)
            gate.release()
            assertFalse(vm.state.value.refreshing)
        }

    /** A LocationRepository whose suspend calls block on a gate until released. */
    private class GatedLocationRepository : LocationRepository {
        private var gate = CompletableDeferred<Unit>()

        fun reset() {
            gate = CompletableDeferred()
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
    }

    @Test
    fun locations_are_displayed_in_position_order_not_repository_order() =
        runTest {
            // Discriminates against a VM that just passes the repository's list
            // through untouched instead of applying orderByPosition (Task 2).
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Bottom", "pantry", 2))
                    items.add(LocationDto(2, "Top", "freezer", 0))
                    items.add(LocationDto(3, "Middle", "fridge", 1))
                }
            val vm = viewModel(repo)

            vm.load(householdId = 1)

            assertEquals(
                listOf("Top", "Middle", "Bottom"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun move_up_swaps_a_location_with_the_one_above_it() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                    items.add(LocationDto(3, "Bottom", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.moveUp(3L)

            assertEquals(
                listOf("Top", "Bottom", "Middle"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun move_down_swaps_a_location_with_the_one_below_it() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                    items.add(LocationDto(3, "Bottom", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.moveDown(1L)

            assertEquals(
                listOf("Middle", "Top", "Bottom"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun move_up_on_the_first_location_does_nothing() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.moveUp(1L)

            assertEquals(
                listOf("Top"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun move_down_on_the_last_location_does_nothing() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.moveDown(1L)

            assertEquals(
                listOf("Top"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun reordering_sends_the_complete_ordered_id_list() =
        runTest {
            // A partial list produces duplicate positions server-side — the
            // server rejects a reorder unless every live location is present.
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                    items.add(LocationDto(3, "Bottom", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.moveUp(3L)

            assertEquals(listOf(1L, 3L, 2L), repo.lastReorderIds)
        }

    @Test
    fun reorder_replaces_local_state_with_the_servers_full_response_not_merged() =
        runTest {
            // THE Task-4 bug, at the locations level: the server's reorder
            // response is already the COMPLETE, current location list (see
            // FakeLocationRepository.reorder's own comment). A VM that merges
            // that response with its own local pre-reorder copy — instead of
            // just replacing state with it — duplicates every location, and a
            // LazyColumn keyed by location.id crashes on the very first reorder.
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                    items.add(LocationDto(3, "Bottom", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.moveUp(3L)

            val ids =
                vm.state.value.locations
                    .map { it.id }
            assertEquals(3, ids.size)
            assertEquals(ids.size, ids.toSet().size)
            assertEquals(
                listOf("Top", "Bottom", "Middle"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun the_optimistic_reorder_frame_is_visible_before_the_server_confirms() =
        runTest {
            // Under the test dispatcher the fake's response normally lands
            // synchronously and overwrites the optimistic frame before any
            // assertion can observe it — gating the fake's reorder() call
            // parks the coroutine mid-flight so the optimistic frame itself
            // can be inspected.
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                    items.add(LocationDto(3, "Bottom", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            repo.reorderGate = CompletableDeferred()

            vm.moveUp(3L)

            // The server call is parked on the gate — this IS the optimistic frame.
            assertEquals(
                listOf("Top", "Bottom", "Middle"),
                vm.state.value.locations
                    .map { it.name },
            )

            repo.reorderGate?.complete(Unit)

            assertEquals(
                listOf("Top", "Bottom", "Middle"),
                vm.state.value.locations
                    .map { it.name },
            )
        }

    @Test
    fun a_rejected_reorder_snaps_the_list_back_to_the_pre_move_order() =
        runTest {
            // Blocker 1 (final review): the server call rewrites every position
            // in one transaction, so a REJECTED reorder (offline, or another
            // member's concurrent edit 422ing this one) must not leave the
            // optimistic frame standing — the list would silently lie about
            // server order for the rest of the screen visit otherwise.
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                    items.add(LocationDto(3, "Bottom", "pantry", 2))
                    failReorder = true
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.moveUp(3L)

            assertEquals(
                listOf("Top", "Middle", "Bottom"),
                vm.state.value.locations
                    .map { it.name },
            )
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun request_delete_does_not_delete_anything_until_confirmed() =
        runTest {
            // The confirm dialog can never be bypassed. Selecting + requesting
            // must NOT mutate the repository or the list — only an explicit
            // confirmDelete() may do that.
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Fridge", "fridge", 0, 0, 3)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)

            vm.requestDelete()

            assertEquals(1, vm.state.value.locations.size)
            assertTrue(repo.batchIdsUsed.isEmpty())
            assertNotNull(vm.state.value.pendingDelete)
            assertEquals(
                3,
                vm.state.value.pendingDelete
                    ?.productCount,
            )
        }

    @Test
    fun deleting_a_location_whose_shelves_are_EMPTY_still_needs_a_strategy() =
        runTest {
            // The trap: the server asks about a location's SHELVES, not its
            // products. A location with 3 empty shelves has product_count 0
            // but shelf_count 3 — contentCount must be built from shelf_count,
            // or this delete goes out with no strategy and 422s.
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Fridge", "fridge", shelf_count = 3, product_count = 0))
                    items.add(LocationDto(2, "Pantry", "pantry", shelf_count = 0, product_count = 0))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            assertNotNull(vm.state.value.pendingDelete)
            assertEquals(
                3,
                vm.state.value.pendingDelete
                    ?.contentCount,
            )
            assertEquals(
                0,
                vm.state.value.pendingDelete
                    ?.productCount,
            )
            assertTrue(
                vm.state.value.pendingDelete
                    ?.needsStrategy == true,
            )
        }

    @Test
    fun requesting_delete_refreshes_the_location_list_so_a_stale_shelf_count_cannot_skip_the_strategy_prompt() =
        runTest {
            // Simulates a shelf added via LocationDetailScreen's own add-shelf
            // sheet — a mutation this screen's cached shelf_count never hears
            // about (see LocationDto.shelf_count's own doc comment).
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Fridge", "fridge", shelf_count = 0, product_count = 0))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)

            // The server-side shelf count changed through a path this VM never
            // observes; its own cached copy (shelf_count = 0) hasn't.
            repo.items[0] = repo.items[0].copy(shelf_count = 1)

            vm.requestDelete()

            assertNotNull(vm.state.value.pendingDelete)
            assertEquals(
                1,
                vm.state.value.pendingDelete
                    ?.contentCount,
            )
            assertTrue(
                vm.state.value.pendingDelete
                    ?.needsStrategy == true,
            )
        }

    @Test
    fun deleting_the_only_location_offers_no_move_target() =
        runTest {
            // There is nowhere to move the contents to, so the dialog must not
            // dangle a "move" option that cannot work. The empty target list IS
            // the mechanism (DeleteStrategyDialog hides a target-requiring option
            // when handed no targets) — there is no canMove flag to assert on.
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Fridge", "fridge", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            assertTrue(
                vm.state.value.moveTargets
                    .isEmpty(),
            )
        }

    @Test
    fun requesting_delete_offers_only_live_non_selected_locations_as_move_targets() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Fridge", "fridge", 0))
                    items.add(LocationDto(2, "Freezer", "freezer", 1))
                    items.add(LocationDto(3, "Pantry", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)

            vm.requestDelete()

            // Not "Fridge" (being deleted) — "Freezer" and "Pantry" only.
            assertEquals(
                listOf(2L, 3L),
                vm.state.value.moveTargets
                    .map { it.id },
            )
        }

    @Test
    fun requesting_delete_with_no_selection_does_nothing() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Fridge", "fridge", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()

            vm.requestDelete()

            assertNull(vm.state.value.pendingDelete)
        }

    @Test
    fun a_rename_updates_the_row_in_place() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Freezr", "freezer", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.rename(1L, "Freezer", "freezer")

            assertEquals(
                "Freezer",
                vm.state.value.locations
                    .first()
                    .name,
            )
            assertEquals(1L, repo.lastRenamedId)
        }

    @Test
    fun confirming_a_delete_sends_the_chosen_strategy_and_one_shared_batch_id() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Bottom", "fridge", 1))
                    items.add(LocationDto(3, "Middle", "pantry", 2))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)

            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.toggleSelection(2L)
            vm.toggleSelection(3L)
            vm.requestDelete()
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            assertEquals(3, repo.batchIdsUsed.size)
            assertTrue(repo.strategiesUsed.all { it == LocationDeleteStrategy.DELETE_CONTENTS })
            // Three locations deleted together = three requests carrying ONE
            // batch id, so one Undo restores all three. A per-request id would
            // split one gesture into three batches and Undo would restore only one.
            assertEquals(1, repo.batchIdsUsed.toSet().size)
            assertEquals(repo.batchIdsUsed.first(), vm.state.value.lastBatchId)
            assertTrue(
                vm.state.value.locations
                    .isEmpty(),
            )
            assertFalse(vm.state.value.editMode)
            assertNull(vm.state.value.pendingDelete)
        }

    @Test
    fun confirming_a_move_delete_sends_the_target_location_id() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            vm.confirmDelete(LocationDeleteStrategy.MOVE_CONTENTS, targetId = 2L)

            assertEquals(listOf(2L), repo.targetIdsUsed)
        }

    @Test
    fun a_batch_delete_that_partially_fails_still_surfaces_undo_for_what_landed() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                    items.add(LocationDto(3, "Bottom", "pantry", 2))
                    failDeleteId = 2L
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.toggleSelection(2L)
            vm.toggleSelection(3L)
            vm.requestDelete()

            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            assertNotNull(vm.state.value.lastBatchId)
            assertEquals(listOf(1L), repo.deletedLocationIds)
            assertEquals(
                setOf(2L, 3L),
                vm.state.value.locations
                    .map { it.id }
                    .toSet(),
            )
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun confirming_delete_ignores_a_selected_id_no_longer_in_the_live_location_list() =
        runTest {
            val repo =
                FakeLocationRepository().apply {
                    items.add(LocationDto(1, "Top", "freezer", 0))
                    items.add(LocationDto(2, "Middle", "fridge", 1))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.toggleSelection(2L)
            vm.requestDelete()

            // Location 2 disappears server-side (another device) while the
            // dialog is open; a refresh updates state.locations but never
            // touches state.selected.
            repo.items.removeIf { it.id == 2L }
            vm.refresh()

            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            assertEquals(listOf(1L), repo.deletedLocationIds)
        }

    @Test
    fun confirming_delete_when_every_selected_location_has_vanished_closes_the_dialog() =
        runTest {
            // Minor 6 (Task 5/5b review): the sibling case to the test above, where
            // ALL selected locations (not just some) vanished from the live list.
            // confirmDelete() used to return early right there, leaving pendingDelete
            // non-null — the dialog stayed open with a Confirm button that no longer
            // did anything, and edit mode/selection were never cleared either.
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            assertNotNull(vm.state.value.pendingDelete)

            // The only selected location disappears server-side (another device)
            // while the dialog is open; a refresh updates state.locations but never
            // touches state.selected.
            repo.items.removeIf { it.id == 1L }
            vm.refresh()

            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            assertTrue("nothing left to delete — no request should fire", repo.batchIdsUsed.isEmpty())
            assertNull("the dialog must close, not hang open with a dead Confirm button", vm.state.value.pendingDelete)
            assertFalse(vm.state.value.editMode)
            assertTrue(
                vm.state.value.selected
                    .isEmpty(),
            )
        }

    @Test
    fun undo_delete_restores_the_batch_and_clears_it() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(repo, restoreRepository = restore)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            val batchId = vm.state.value.lastBatchId
            assertNotNull(batchId)

            vm.undoDelete()

            assertEquals(1L, restore.lastHouseholdId)
            assertEquals(batchId, restore.lastBatchId)
            assertNull(vm.state.value.lastBatchId)
            // ALSO FIX (final review): a successful undo sets undoResult so the
            // screen can show the "Restored." snackbar (delete_undone).
            assertEquals(UndoOutcome.SUCCESS, vm.state.value.undoResult)
        }

    @Test
    fun a_failed_undo_sets_the_failure_result_instead_of_a_generic_error() =
        runTest {
            // ALSO FIX (final review): a 409 from the restore endpoint (already
            // restored elsewhere, or past the undo window) used to fall through to
            // launchLoading's generic "Something went wrong." — undoResult now
            // carries the specific outcome instead, and the generic error stays
            // unset so the screen doesn't show BOTH messages.
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(repo, restoreRepository = restore)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            restore.fail = true

            vm.undoDelete()

            assertEquals(UndoOutcome.FAILURE, vm.state.value.undoResult)
            assertNull(vm.state.value.error)
            // The batch id survives a failed undo — nothing was actually restored.
            assertNotNull(vm.state.value.lastBatchId)
        }

    @Test
    fun consume_undo_result_clears_it() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(repo, restoreRepository = restore)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            vm.undoDelete()
            assertNotNull(vm.state.value.undoResult)

            vm.consumeUndoResult()

            assertNull(vm.state.value.undoResult)
        }

    @Test
    fun consume_last_batch_clears_it_without_restoring() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(repo, restoreRepository = restore)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            vm.consumeLastBatch()

            assertNull(vm.state.value.lastBatchId)
            assertEquals(0, restore.restoreCalls)
        }

    @Test
    fun cancel_delete_closes_the_dialog_without_deleting_anything() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            vm.cancelDelete()

            assertNull(vm.state.value.pendingDelete)
            assertEquals(1, vm.state.value.locations.size)
            assertTrue(repo.batchIdsUsed.isEmpty())
        }

    @Test
    fun exiting_edit_mode_clears_selection_and_any_pending_delete() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Top", "freezer", 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            vm.exitEditMode()

            assertFalse(vm.state.value.editMode)
            assertTrue(
                vm.state.value.selected
                    .isEmpty(),
            )
            assertNull(vm.state.value.pendingDelete)
        }

    // hierarchyStore.refresh() coverage (Minor 3, Task 5/5b review) now lives in
    // StorageOverviewViewModelHierarchyRefreshTest.kt — split out of this class
    // (final review, detekt LargeClass) once the Blocker-1 snap-back test pushed
    // this file over detekt's size threshold.
}
