package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.hierarchy.ShelfDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.ShelfDeletion
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.settings.ShelfViewStore
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShelvesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeShelfRepository : ShelfRepository {
        val items = mutableListOf<ShelfDto>()
        var failList = false

        // Recorded for assertions below — the real ShelfRepository interface bundles
        // batchId/strategy/targetShelfId into one ShelfDeletion (data/hierarchy/DeleteStrategy.kt),
        // so each call's deletion is captured whole and unpacked into these lists.
        val batchIdsUsed = mutableListOf<String>()
        val strategiesUsed = mutableListOf<ShelfDeleteStrategy?>()
        val targetIdsUsed = mutableListOf<Long?>()
        val deletedShelfIds = mutableListOf<Long>()
        var lastRenamedId: Long? = null
        var lastReorderIds: List<Long>? = null

        // When set, deleteWithStrategy throws for this one id instead of deleting
        // it — simulates a mid-batch server failure (Minor 4: partial batches).
        var failDeleteId: Long? = null

        override fun getCached(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto>? = null

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ): ShelfDto {
            val dto =
                ShelfDto(id = (items.size + 1).toLong(), name = name, position = items.size, location_id = locationId)
            items.add(dto)
            return dto
        }

        override suspend fun rename(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
            name: String,
        ): ShelfDto {
            lastRenamedId = shelfId
            val index = items.indexOfFirst { it.id == shelfId }
            val updated = items[index].copy(name = name)
            items[index] = updated
            return updated
        }

        var reorderGate: CompletableDeferred<Unit>? = null

        override suspend fun reorder(
            householdId: Long,
            locationId: Long,
            ids: List<Long>,
        ): List<ShelfDto> {
            reorderGate?.await()
            lastReorderIds = ids
            // Only positions of the given ids change...
            ids.forEachIndexed { i, id ->
                val index = items.indexOfFirst { it.id == id }
                if (index >= 0) items[index] = items[index].copy(position = i)
            }
            // ...but the FULL list for the location is returned, system shelf
            // included: PATCH .../shelves/reorder ends with `return
            // $this->index(...)` server-side. A fake that returned only the
            // reordered subset would hide the exact bug this fake exists to
            // catch (the VM appending its own local system-shelf copy on top
            // of the one the server already sent back → a duplicate id that
            // crashes the LazyColumn's itemsIndexed(key = shelf.id)).
            return items.toList()
        }

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
            deletion: ShelfDeletion,
        ) {
            if (shelfId == failDeleteId) throw RuntimeException("delete failed")
            batchIdsUsed += deletion.batchId
            strategiesUsed += deletion.strategy
            targetIdsUsed += deletion.targetShelfId
            deletedShelfIds += shelfId
            items.removeIf { it.id == shelfId }
        }
    }

    private class FakeRestoreRepository : RestoreRepository {
        var lastHouseholdId: Long? = null
        var lastBatchId: String? = null
        var restoreCalls = 0

        override suspend fun restore(
            householdId: Long,
            batchId: String,
        ): Int {
            lastHouseholdId = householdId
            lastBatchId = batchId
            restoreCalls++
            return 1
        }
    }

    private class FakeShelfViewStore(
        initial: Boolean = false,
    ) : ShelfViewStore {
        var stored = initial

        override fun isListView(): Boolean = stored

        override fun setListView(listView: Boolean) {
            stored = listView
        }
    }

    private class FakeHouseholdRepository : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = emptyList()

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = Unit
    }

    private fun viewModel(
        repo: FakeShelfRepository = FakeShelfRepository(),
        restoreRepository: RestoreRepository = FakeRestoreRepository(),
        shelfViewStore: ShelfViewStore = FakeShelfViewStore(),
    ): ShelvesViewModel {
        val hierarchyStore = TestHierarchy.store(FakeHouseholdRepository())
        return ShelvesViewModel(repo, restoreRepository, shelfViewStore, hierarchyStore)
    }

    @Test
    fun load_populates_shelves() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)

            vm.load(householdId = 1, locationId = 1)

            assertEquals(1, vm.state.value.shelves.size)
            assertEquals(
                "Top",
                vm.state.value.shelves
                    .first()
                    .name,
            )
        }

    @Test
    fun create_adds_a_shelf_and_clears_the_field() =
        runTest {
            val vm = viewModel()
            vm.load(householdId = 1, locationId = 1)
            vm.onNewNameChange("Middle")

            vm.create()

            assertTrue(
                vm.state.value.shelves
                    .any { it.name == "Middle" },
            )
            assertEquals("", vm.state.value.newName)
        }

    @Test
    fun list_failure_surfaces_an_error() =
        runTest {
            val repo = FakeShelfRepository().apply { failList = true }
            val vm = viewModel(repo)

            vm.load(householdId = 1, locationId = 1)

            assertEquals("offline", vm.state.value.error)
        }

    @Test
    fun shelves_are_displayed_in_position_order_not_repository_order() =
        runTest {
            // Discriminates against a VM that just passes the repository's list
            // through untouched instead of applying orderByPosition (Task 2).
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Bottom", 2, 1L))
                    items.add(ShelfDto(2, "Top", 0, 1L))
                    items.add(ShelfDto(3, "Middle", 1, 1L))
                }
            val vm = viewModel(repo)

            vm.load(householdId = 1, locationId = 1)

            assertEquals(
                listOf("Top", "Middle", "Bottom"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun the_unsorted_shelf_always_sorts_last_regardless_of_its_position() =
        runTest {
            // The system shelf is never part of the manual drag order, so a low
            // position value here must NOT float it above the user's own shelves.
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Unsorted", 0, 1L, is_system = true))
                    items.add(ShelfDto(2, "Bottom", 2, 1L))
                    items.add(ShelfDto(3, "Top", 1, 1L))
                }
            val vm = viewModel(repo)

            vm.load(householdId = 1, locationId = 1)

            assertEquals(
                listOf("Top", "Bottom", "Unsorted"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun move_up_swaps_a_shelf_with_the_one_above_it() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.moveUp(3L)

            assertEquals(
                listOf("Top", "Bottom", "Middle"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun move_down_swaps_a_shelf_with_the_one_below_it() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.moveDown(1L)

            assertEquals(
                listOf("Middle", "Top", "Bottom"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun move_up_on_the_first_shelf_does_nothing() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.moveUp(1L)

            assertEquals(
                listOf("Top"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun move_down_on_the_last_shelf_does_nothing() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.moveDown(1L)

            assertEquals(
                listOf("Top"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun reordering_sends_the_complete_id_list_excluding_the_system_shelf() =
        runTest {
            // A partial list produces duplicate positions server-side, and the
            // server does not want the system shelf in the payload at all.
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                    items.add(ShelfDto(4, "Unsorted", 3, 1L, is_system = true))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.moveUp(3L)

            assertEquals(listOf(1L, 3L, 2L), repo.lastReorderIds)
            // The system shelf still renders, still last, untouched by the reorder.
            assertEquals(
                listOf("Top", "Bottom", "Middle", "Unsorted"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun the_optimistic_reorder_frame_keeps_the_system_shelf_before_the_server_confirms() =
        runTest {
            // Under the test dispatcher the fake's response normally lands
            // synchronously and overwrites the optimistic frame before any
            // assertion can observe it — so a VM that mutated the optimistic
            // frame to `shelves = reordered` (silently dropping the system
            // shelf) would still pass every other test in this file. Gating
            // the fake's reorder() call parks the coroutine mid-flight so the
            // optimistic frame itself can be inspected.
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                    items.add(ShelfDto(4, "Unsorted", 3, 1L, is_system = true))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            repo.reorderGate = CompletableDeferred()

            vm.moveUp(3L)

            // The server call is parked on the gate — this IS the optimistic frame.
            assertEquals(
                listOf("Top", "Bottom", "Middle", "Unsorted"),
                vm.state.value.shelves
                    .map { it.name },
            )

            repo.reorderGate?.complete(Unit)

            // And it still holds once the (faithful, full-list) server response lands.
            assertEquals(
                listOf("Top", "Bottom", "Middle", "Unsorted"),
                vm.state.value.shelves
                    .map { it.name },
            )
        }

    @Test
    fun the_unsorted_shelf_cannot_be_selected_for_deletion() =
        runTest {
            // It is a system shelf: it holds the products the user chose to KEEP.
            // Letting a stray checkbox tap destroy it would defeat the entire point.
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Unsorted", 1, 1L, is_system = true))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.enterEditMode()
            vm.toggleSelection(2L)

            assertTrue(
                vm.state.value.selected
                    .isEmpty(),
            )
        }

    @Test
    fun rename_updates_the_shelf_name() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.rename(1L, "Freezer top")

            assertEquals(1L, repo.lastRenamedId)
            assertEquals(
                "Freezer top",
                vm.state.value.shelves
                    .first()
                    .name,
            )
        }

    @Test
    fun the_unsorted_shelf_cannot_be_renamed() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Unsorted", 0, 1L, is_system = true)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.rename(1L, "My custom name")

            assertEquals(
                "Unsorted",
                vm.state.value.shelves
                    .first()
                    .name,
            )
            assertNull(repo.lastRenamedId)
        }

    @Test
    fun entering_edit_mode_forces_the_list_view() =
        runTest {
            // Tabs cannot host reorder buttons or inline rename. The flip is also
            // why the manual list/tab toggle exists: by the time the user first
            // enters edit mode, the list is somewhere they have already been.
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            assertFalse(vm.state.value.listView)

            vm.enterEditMode()
            assertTrue(vm.state.value.listView)

            vm.exitEditMode()
            assertFalse(vm.state.value.listView)
        }

    @Test
    fun exiting_edit_mode_restores_the_view_the_user_had_chosen() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.toggleListView()
            assertTrue(vm.state.value.listView)

            vm.enterEditMode()
            vm.exitEditMode()

            assertTrue(vm.state.value.listView)
        }

    @Test
    fun toggle_list_view_flips_state_and_persists_the_preference() =
        runTest {
            val store = FakeShelfViewStore(initial = false)
            val vm = viewModel(shelfViewStore = store)
            vm.load(householdId = 1, locationId = 1)

            vm.toggleListView()

            assertTrue(vm.state.value.listView)
            assertTrue(store.stored)

            vm.toggleListView()

            assertFalse(vm.state.value.listView)
            assertFalse(store.stored)
        }

    @Test
    fun request_delete_does_not_delete_anything_until_confirmed() =
        runTest {
            // The whole point of Task 4: the confirm dialog can never be bypassed.
            // Selecting + requesting must NOT mutate the repository or the list —
            // only an explicit confirmDelete() may do that.
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L, product_count = 3)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)

            vm.requestDelete()

            assertEquals(1, vm.state.value.shelves.size)
            assertTrue(repo.batchIdsUsed.isEmpty())
            assertNotNull(vm.state.value.pendingDelete)
            assertEquals(
                3,
                vm.state.value.pendingDelete
                    ?.productCount,
            )
            // Resolution #2: a shelf's contentCount IS its productCount.
            assertEquals(
                3,
                vm.state.value.pendingDelete
                    ?.contentCount,
            )
        }

    @Test
    fun requesting_delete_refreshes_the_shelf_list_so_a_stale_product_count_cannot_skip_the_strategy_prompt() =
        runTest {
            // Simulates a product added via the FAB / barcode scan / product
            // delete — none of those mutations update ShelvesViewModel's own
            // cached shelves list (see ShelfDto.product_count's doc comment).
            // requestDelete() must re-fetch before building the plan, or a
            // non-empty shelf gets deleted with no strategy and the server 422s.
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Snacks", 0, 1L, product_count = 0)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)

            // The server-side count changed through a path this VM never
            // observes; its own cached copy (product_count = 0) hasn't.
            repo.items[0] = repo.items[0].copy(product_count = 1)

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
    fun requesting_delete_offers_only_live_non_system_non_selected_shelves_as_move_targets() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Unsorted", 2, 1L, is_system = true))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)

            vm.requestDelete()

            // Not "Top" (being deleted), not "Unsorted" (system) — only "Middle".
            assertEquals(
                listOf(2L),
                vm.state.value.moveTargets
                    .map { it.id },
            )
        }

    @Test
    fun requesting_delete_with_no_selection_does_nothing() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()

            vm.requestDelete()

            assertNull(vm.state.value.pendingDelete)
        }

    @Test
    fun confirming_a_delete_sends_the_chosen_strategy_and_one_shared_batch_id() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Bottom", 1, 1L))
                    items.add(ShelfDto(3, "Middle", 2, 1L))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)

            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.toggleSelection(2L)
            vm.toggleSelection(3L)
            vm.requestDelete()
            vm.confirmDelete(ShelfDeleteStrategy.DELETE_PRODUCTS, targetId = null)

            assertEquals(3, repo.batchIdsUsed.size)
            assertTrue(repo.strategiesUsed.all { it == ShelfDeleteStrategy.DELETE_PRODUCTS })
            // Three shelves deleted together = three requests carrying ONE batch id,
            // so one Undo restores all three. A per-request id would split one
            // gesture into three batches and Undo would restore only one.
            assertEquals(1, repo.batchIdsUsed.toSet().size)
            assertEquals(repo.batchIdsUsed.first(), vm.state.value.lastBatchId)
            assertTrue(
                vm.state.value.shelves
                    .isEmpty(),
            )
            assertFalse(vm.state.value.editMode)
            assertNull(vm.state.value.pendingDelete)
        }

    @Test
    fun confirming_a_move_delete_sends_the_target_shelf_id() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            vm.confirmDelete(ShelfDeleteStrategy.MOVE_PRODUCTS, targetId = 2L)

            assertEquals(listOf(2L), repo.targetIdsUsed)
        }

    @Test
    fun confirming_a_delete_restores_the_view_the_user_had_before_entering_edit_mode() =
        runTest {
            // exitEditMode() (Cancel) and confirmDelete() (Delete) must leave the
            // screen in the same shape — both restore the pre-edit view, not just
            // exit editMode.
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            assertFalse(vm.state.value.listView)

            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(null, targetId = null)

            assertFalse(vm.state.value.editMode)
            assertFalse(vm.state.value.listView)
        }

    @Test
    fun a_batch_delete_that_partially_fails_still_surfaces_undo_for_what_landed() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                    failDeleteId = 2L
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.toggleSelection(2L)
            vm.toggleSelection(3L)
            vm.requestDelete()

            vm.confirmDelete(ShelfDeleteStrategy.DELETE_PRODUCTS, targetId = null)

            // Shelf 1's delete landed before shelf 2's failed — that must still be
            // Undo-able, and the list must be reconciled with what the server
            // actually holds now (1 gone; 2 and 3 remain — we stop at the first
            // failure rather than attempting the rest blind).
            assertNotNull(vm.state.value.lastBatchId)
            assertEquals(listOf(1L), repo.deletedShelfIds)
            assertEquals(
                setOf(2L, 3L),
                vm.state.value.shelves
                    .map { it.id }
                    .toSet(),
            )
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun confirming_delete_ignores_a_selected_id_no_longer_in_the_live_shelf_list() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.toggleSelection(2L)
            vm.requestDelete()

            // Shelf 2 disappears server-side (another device) while the dialog is
            // open; a refresh (e.g. a live update) updates state.shelves but never
            // touches state.selected.
            repo.items.removeIf { it.id == 2L }
            vm.refresh()

            vm.confirmDelete(ShelfDeleteStrategy.DELETE_PRODUCTS, targetId = null)

            // Only shelf 1 (still live) gets a delete request — requestDelete()
            // filters its plan the same way, so confirmDelete must use the same
            // filtered set, not the raw (possibly stale) selection.
            assertEquals(listOf(1L), repo.deletedShelfIds)
        }

    @Test
    fun undo_delete_restores_the_batch_and_clears_it() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(repo, restoreRepository = restore)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(ShelfDeleteStrategy.DELETE_PRODUCTS, targetId = null)
            val batchId = vm.state.value.lastBatchId
            assertNotNull(batchId)

            vm.undoDelete()

            assertEquals(1L, restore.lastHouseholdId)
            assertEquals(batchId, restore.lastBatchId)
            assertNull(vm.state.value.lastBatchId)
        }

    @Test
    fun consume_last_batch_clears_it_without_restoring() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(repo, restoreRepository = restore)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()
            vm.confirmDelete(ShelfDeleteStrategy.DELETE_PRODUCTS, targetId = null)

            vm.consumeLastBatch()

            assertNull(vm.state.value.lastBatchId)
            assertEquals(0, restore.restoreCalls)
        }

    @Test
    fun cancel_delete_closes_the_dialog_without_deleting_anything() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val vm = viewModel(repo)
            vm.load(householdId = 1, locationId = 1)
            vm.enterEditMode()
            vm.toggleSelection(1L)
            vm.requestDelete()

            vm.cancelDelete()

            assertNull(vm.state.value.pendingDelete)
            assertEquals(1, vm.state.value.shelves.size)
            assertTrue(repo.batchIdsUsed.isEmpty())
        }
}
