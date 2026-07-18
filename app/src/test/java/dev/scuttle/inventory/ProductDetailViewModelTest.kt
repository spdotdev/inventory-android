package dev.scuttle.inventory

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.products.ProductDetailViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ProductDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun savedState(
        householdId: Long = 1L,
        shelfId: Long = 1L,
        productId: Long = 1L,
    ) = SavedStateHandle(
        mapOf("householdId" to householdId, "shelfId" to shelfId, "productId" to productId),
    )

    private class FakeProductRepository(
        items: List<ProductDto> = emptyList(),
    ) : ProductRepository {
        val items = items.toMutableList()
        var failList = false
        var failUpdate = false
        var failDelete = false
        var failMutate = false
        var deleteBatchId = "batch-detail"
        var addCalls = 0
        var removeCalls = 0

        override fun getCached(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto>? = null

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto> {
            if (failList) throw RuntimeException("network error")
            return items.toList()
        }

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            name: String,
            quantity: Int,
            code: String?,
        ): ProductDto = ProductDto(99, name, quantity, shelfId).also { items.add(it) }

        override suspend fun update(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            edit: ProductEdit,
        ): ProductDto {
            if (failUpdate) throw RuntimeException("save failed")
            val updated = ProductDto(productId, edit.name, 0, shelfId, edit.description, edit.code, edit.isMandatory)
            val idx = items.indexOfFirst { it.id == productId }
            if (idx >= 0) items[idx] = updated
            return updated
        }

        override suspend fun add(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ): ProductDto {
            if (failMutate) throw RuntimeException("add failed")
            addCalls++
            val idx = items.indexOfFirst { it.id == productId }
            val updated = items[idx].copy(quantity = items[idx].quantity + amount)
            items[idx] = updated
            return updated
        }

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ): ProductDto {
            if (failMutate) throw RuntimeException("remove failed")
            removeCalls++
            val idx = items.indexOfFirst { it.id == productId }
            val updated = items[idx].copy(quantity = (items[idx].quantity - amount).coerceAtLeast(0))
            items[idx] = updated
            return updated
        }

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            targetShelfId: Long,
        ): ProductDto = items.first { it.id == productId }

        override suspend fun delete(
            householdId: Long,
            shelfId: Long,
            productId: Long,
        ): String {
            if (failDelete) throw RuntimeException("delete failed")
            items.removeIf { it.id == productId }
            return deleteBatchId
        }

        override suspend fun uploadImage(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            imageUri: Uri,
            mimeType: String,
        ): ProductDto = items.first { it.id == productId }
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

    /** Mirrors ProductsViewModelTest's minimal fake — no household data needed by these tests. */
    private class FakeHouseholdRepository : HouseholdRepository {
        override fun getCached() = emptyList<HouseholdDto>()

        override suspend fun list() = emptyList<HouseholdDto>()

        override suspend fun create(name: String) =
            HouseholdDto(1, name, "", role = "admin", can_restructure = true, can_manage_members = true)

        override suspend fun join(code: String) =
            HouseholdDto(1, "", code, role = "admin", can_restructure = true, can_manage_members = true)

        override suspend fun leave(householdId: Long) = Unit
    }

    private fun viewModel(
        savedStateHandle: SavedStateHandle = savedState(),
        repository: ProductRepository = FakeProductRepository(),
        restoreRepository: RestoreRepository = FakeRestoreRepository(),
        hierarchyStore: HierarchyStore = TestHierarchy.store(FakeHouseholdRepository()),
    ): ProductDetailViewModel = ProductDetailViewModel(savedStateHandle, repository, restoreRepository, hierarchyStore)

    @Test
    fun load_finds_product_by_id() =
        runTest {
            val product = ProductDto(id = 42, name = "Milk", quantity = 2, shelf_id = 1)
            val vm = viewModel(savedState(productId = 42), FakeProductRepository(listOf(product)))

            assertEquals(
                "Milk",
                vm.state.value.product
                    ?.name,
            )
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun load_sets_null_when_product_not_found() =
        runTest {
            val vm = viewModel(savedState(productId = 99), FakeProductRepository())

            assertNull(vm.state.value.product)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun load_failure_surfaces_error() =
        runTest {
            val repo = FakeProductRepository().apply { failList = true }
            val vm = viewModel(savedState(), repo)

            assertNotNull(vm.state.value.loadErrorRes)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun save_updates_product_and_sets_saved() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val vm = viewModel(savedState(), FakeProductRepository(listOf(product)))

            vm.save(ProductEdit("Oat Milk", "lactose free", null, isMandatory = true, lowStockThreshold = null))

            assertTrue(vm.state.value.saved)
            assertEquals(
                "Oat Milk",
                vm.state.value.product
                    ?.name,
            )
            assertTrue(
                vm.state.value.product
                    ?.is_mandatory == true,
            )
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun save_failure_surfaces_error_and_does_not_set_saved() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failUpdate = true }
            val vm = viewModel(savedState(), repo)

            vm.save(ProductEdit("Oat Milk", null, null, false, null))

            assertFalse(vm.state.value.saved)
            assertNotNull(vm.state.value.errorRes)
        }

    @Test
    fun save_persists_mandatory_flag() =
        runTest {
            val product = ProductDto(id = 1, name = "Eggs", quantity = 0, shelf_id = 1, is_mandatory = false)
            val repo = FakeProductRepository(listOf(product))
            val vm = viewModel(savedState(), repo)

            vm.save(ProductEdit("Eggs", null, null, isMandatory = true, lowStockThreshold = null))

            assertTrue(repo.items.first().is_mandatory == true)
        }

    @Test
    fun delete_removes_product_and_sets_deleted() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val vm = viewModel(savedState(), repo)

            vm.delete()

            assertTrue(vm.state.value.deleted)
            assertTrue(repo.items.isEmpty())
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun delete_captures_the_server_minted_batch_id_for_undo() =
        runTest {
            // Product delete is the app's single most frequent destructive action —
            // this is what makes it Undo-able, mirroring Shelves/Storage/Drawer.
            // Unlike THOSE deletes, the batch id is server-minted (ProductController::
            // destroy), not client-minted, so this pins that the VM actually reads it
            // back off the repository call instead of discarding it (the old bodyless
            // ProductApi.delete() bug this task exists to fix).
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { deleteBatchId = "server-batch-99" }
            val vm = viewModel(savedState(), repo)

            vm.delete()

            assertEquals("server-batch-99", vm.state.value.lastBatchId)
        }

    @Test
    fun undo_delete_restores_the_batch_and_clears_it() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val restore = FakeRestoreRepository()
            val vm = viewModel(savedState(), repo, restore)
            vm.delete()
            val batchId = vm.state.value.lastBatchId
            assertNotNull(batchId)

            vm.undoDelete()

            assertEquals(1L, restore.lastHouseholdId)
            assertEquals(batchId, restore.lastBatchId)
            assertNull(vm.state.value.lastBatchId)
            assertEquals(UndoOutcome.SUCCESS, vm.state.value.undoResult)
        }

    @Test
    fun a_failed_undo_sets_the_failure_result_instead_of_a_generic_error() =
        runTest {
            // A 409 (already restored elsewhere, or past the undo window) must not
            // fall through to a generic "Something went wrong." error.
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val restore = FakeRestoreRepository().apply { fail = true }
            val vm = viewModel(savedState(), repo, restore)
            vm.delete()

            vm.undoDelete()

            assertEquals(UndoOutcome.FAILURE, vm.state.value.undoResult)
            assertNull(vm.state.value.errorRes)
            // The batch id survives a failed undo — nothing was actually restored.
            assertNotNull(vm.state.value.lastBatchId)
        }

    @Test
    fun consume_last_batch_clears_it_without_restoring() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val restore = FakeRestoreRepository()
            val vm = viewModel(savedState(), repo, restore)
            vm.delete()

            vm.consumeLastBatch()

            assertNull(vm.state.value.lastBatchId)
            assertEquals(0, restore.restoreCalls)
        }

    @Test
    fun consume_undo_result_clears_it() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val restore = FakeRestoreRepository()
            val vm = viewModel(savedState(), repo, restore)
            vm.delete()
            vm.undoDelete()
            assertNotNull(vm.state.value.undoResult)

            vm.consumeUndoResult()

            assertNull(vm.state.value.undoResult)
        }

    @Test
    fun delete_failure_surfaces_error() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failDelete = true }
            val vm = viewModel(savedState(), repo)

            vm.delete()

            assertFalse(vm.state.value.deleted)
            assertNotNull(vm.state.value.errorRes)
        }

    @Test
    fun increment_adds_one_and_updates_state_product() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val vm = viewModel(savedState(), repo)

            vm.increment()

            assertEquals(1, repo.addCalls)
            assertFalse(vm.state.value.loading)
            assertNotNull(vm.state.value.product)
        }

    @Test
    fun decrement_removes_one_when_quantity_is_positive() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val vm = viewModel(savedState(), repo)

            vm.decrement()

            assertEquals(1, repo.removeCalls)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun decrement_is_a_no_op_at_zero_quantity() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 0, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val vm = viewModel(savedState(), repo)

            vm.decrement()

            assertEquals(0, repo.removeCalls)
        }

    // H2: a failed +/- mutation no longer surfaces the generic `error` snackbar — it sets the
    // specific quantityMutationFailed one-shot flag instead (see ProductDetailUiState's doc
    // comment), and bumps quantityMutationEpoch so the screen's optimistic pendingDelta resets
    // even though product.quantity — the mutation failed — never changed.
    @Test
    fun increment_failure_sets_the_specific_failure_flag_and_bumps_the_epoch() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failMutate = true }
            val vm = viewModel(savedState(), repo)
            val epochBefore = vm.state.value.quantityMutationEpoch

            vm.increment()

            assertNull(vm.state.value.errorRes)
            assertTrue(vm.state.value.quantityMutationFailed)
            assertEquals(epochBefore + 1, vm.state.value.quantityMutationEpoch)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun decrement_failure_sets_the_specific_failure_flag_and_bumps_the_epoch() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failMutate = true }
            val vm = viewModel(savedState(), repo)
            val epochBefore = vm.state.value.quantityMutationEpoch

            vm.decrement()

            assertNull(vm.state.value.errorRes)
            assertTrue(vm.state.value.quantityMutationFailed)
            assertEquals(epochBefore + 1, vm.state.value.quantityMutationEpoch)
        }

    @Test
    fun successful_increment_bumps_the_epoch_without_setting_the_failure_flag() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val vm = viewModel(savedState(), repo)
            val epochBefore = vm.state.value.quantityMutationEpoch

            vm.increment()

            assertEquals(epochBefore + 1, vm.state.value.quantityMutationEpoch)
            assertFalse(vm.state.value.quantityMutationFailed)
        }

    @Test
    fun consumeQuantityMutationFailed_clears_the_one_shot_flag() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failMutate = true }
            val vm = viewModel(savedState(), repo)
            vm.increment()
            assertTrue(vm.state.value.quantityMutationFailed)

            vm.consumeQuantityMutationFailed()

            assertFalse(vm.state.value.quantityMutationFailed)
        }

    @Test
    fun consumeError_clears_the_one_shot_error_after_it_is_shown() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failUpdate = true }
            val vm = viewModel(savedState(), repo)

            vm.save(ProductEdit("Oat Milk", null, null, false, null))
            assertNotNull(vm.state.value.errorRes)

            // After the Snackbar has shown it, the error is consumed so it doesn't re-fire.
            vm.consumeError()
            assertNull(vm.state.value.errorRes)
        }

    // GAP6-M1: a remote household.changed ping arrives as hierarchyStore.refresh() only
    // (LiveUpdates.kt) — ProductDetailViewModel must silently re-fetch this product so
    // the screen doesn't go stale (e.g. after another member edits/moves it) until a
    // manual pull-to-refresh.
    private class CountingProductRepository(
        items: List<ProductDto> = emptyList(),
    ) : ProductRepository {
        val items = items.toMutableList()
        var listCalls = 0
        var inFlight: CompletableDeferred<Unit>? = null

        override fun getCached(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto>? = null

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto> {
            listCalls++
            inFlight?.await()
            return items.toList()
        }

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            name: String,
            quantity: Int,
            code: String?,
        ) = throw NotImplementedError()

        override suspend fun update(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            edit: ProductEdit,
        ) = throw NotImplementedError()

        override suspend fun add(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = throw NotImplementedError()

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = throw NotImplementedError()

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            targetShelfId: Long,
        ) = throw NotImplementedError()

        override suspend fun uploadImage(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            imageUri: Uri,
            mimeType: String,
        ) = throw NotImplementedError()

        override suspend fun delete(
            householdId: Long,
            shelfId: Long,
            productId: Long,
        ) = throw NotImplementedError()
    }

    // NOTE: init both calls load() directly AND subscribes to hierarchyStore.state, whose
    // initial (already-current) value replays immediately under UnconfinedTestDispatcher —
    // so a fresh VM makes 2 list() calls at construction, not 1 (both no-ops here, matching
    // HouseholdsViewModel's own observeHierarchyStore() init-time behavior).
    @Test
    fun a_hierarchy_store_refresh_re_fetches_the_product() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = CountingProductRepository(listOf(product))
            val store = TestHierarchy.store(FakeHouseholdRepository())
            viewModel(repository = repo, hierarchyStore = store)
            val callsAfterInit = repo.listCalls

            store.refresh()

            assertEquals(callsAfterInit + 1, repo.listCalls)
        }

    @Test
    fun no_store_refresh_signal_triggers_no_extra_fetch_beyond_init() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = CountingProductRepository(listOf(product))
            val store = TestHierarchy.store(FakeHouseholdRepository())
            viewModel(repository = repo, hierarchyStore = store)
            val callsAfterInit = repo.listCalls

            // No store.refresh() call at all — the store's own state never changes again,
            // so nothing beyond init's own fetches happens.
            assertEquals(callsAfterInit, repo.listCalls)
        }

    @Test
    fun a_hierarchy_store_refresh_during_an_in_flight_local_mutation_does_not_clobber_it() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val gate = CompletableDeferred<Unit>()
            val repo = CountingProductRepository(listOf(product))
            val store = TestHierarchy.store(FakeHouseholdRepository())
            val vm = viewModel(repository = repo, hierarchyStore = store)
            val callsAfterInit = repo.listCalls

            // Start a local reload that stays in-flight (e.g. a manual pull-to-refresh).
            repo.inFlight = gate
            vm.load()
            assertTrue(vm.state.value.loading)
            assertEquals(callsAfterInit + 1, repo.listCalls)

            // A remote ping lands mid-flight — must not fire another, clobbering fetch.
            store.refresh()
            assertEquals(callsAfterInit + 1, repo.listCalls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(callsAfterInit + 1, repo.listCalls)
        }
}
