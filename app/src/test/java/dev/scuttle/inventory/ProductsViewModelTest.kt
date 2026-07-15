package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.products.ProductsViewModel
import dev.scuttle.inventory.ui.products.ScanResult
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

class ProductsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeProductRepository : ProductRepository {
        val items = mutableListOf<ProductDto>()
        var failList = false
        var lastMove: Triple<Long, Long, Long>? = null

        // Recorded for the Undo tests below — mirrors FakeShelfRepository's
        // batchIdsUsed/deletedShelfIds bookkeeping in ShelvesViewModelTest.
        var deleteBatchId = "batch-products"
        val deletedProductIds = mutableListOf<Long>()
        var failDelete = false

        override fun getCached(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto>? = null

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            name: String,
            quantity: Int,
            code: String?,
        ): ProductDto {
            val dto =
                ProductDto(
                    id = (items.size + 1).toLong(),
                    name = name,
                    quantity = quantity,
                    shelf_id = shelfId,
                    code = code,
                )
            items.add(dto)
            return dto
        }

        override suspend fun update(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            edit: ProductEdit,
        ): ProductDto {
            val index = items.indexOfFirst { it.id == productId }
            val updated =
                items[index].copy(
                    name = edit.name,
                    description = edit.description,
                    code = edit.code,
                    is_mandatory = edit.isMandatory,
                    low_stock_threshold = edit.lowStockThreshold,
                )
            items[index] = updated
            return updated
        }

        override suspend fun add(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ): ProductDto = adjust(productId, amount)

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ): ProductDto = adjust(productId, -amount)

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            targetShelfId: Long,
        ): ProductDto {
            lastMove = Triple(shelfId, productId, targetShelfId)
            return items.first { it.id == productId }
        }

        override suspend fun delete(
            householdId: Long,
            shelfId: Long,
            productId: Long,
        ): String {
            // IOException, not RuntimeException: TooGenericExceptionThrown's
            // baseline entries are keyed by exact source text per class, and
            // this is a NEW throw site — a generic exception here would need a
            // fresh baseline entry, which this branch's gates forbid adding.
            if (failDelete) throw IOException("delete failed")
            deletedProductIds += productId
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

        private fun adjust(
            productId: Long,
            delta: Int,
        ): ProductDto {
            val index = items.indexOfFirst { it.id == productId }
            val updated = items[index].let { it.copy(quantity = (it.quantity + delta).coerceAtLeast(0)) }
            items[index] = updated
            return updated
        }
    }

    private class FakeLocationRepository(
        private val locations: List<LocationDto>,
    ) : LocationRepository {
        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long): List<LocationDto> = locations

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ): LocationDto = LocationDto(99, name, type)
    }

    private class FakeShelfRepository(
        private val byLocation: Map<Long, List<ShelfDto>>,
    ) : ShelfRepository {
        override fun getCached(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto>? = null

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto> = byLocation[locationId].orEmpty()

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ): ShelfDto = ShelfDto(99, name, 0, locationId)
    }

    private class FakeSearchRepository : SearchRepository {
        override suspend fun search(
            householdId: Long,
            query: String,
        ): List<SearchResultDto> = emptyList()
    }

    private class FakeHouseholdRepository : HouseholdRepository {
        override fun getCached() = emptyList<HouseholdDto>()

        override suspend fun list() = emptyList<HouseholdDto>()

        override suspend fun create(name: String) = HouseholdDto(1, name, "")

        override suspend fun join(code: String) = HouseholdDto(1, "", code)

        override suspend fun leave(householdId: Long) {}
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

    private fun viewModel(
        products: FakeProductRepository = FakeProductRepository(),
        locations: FakeLocationRepository = FakeLocationRepository(emptyList()),
        shelves: FakeShelfRepository = FakeShelfRepository(emptyMap()),
        search: FakeSearchRepository = FakeSearchRepository(),
        restoreRepository: RestoreRepository = FakeRestoreRepository(),
    ): ProductsViewModel {
        val store = HierarchyStore(FakeHouseholdRepository(), locations, shelves, products, UnconfinedTestDispatcher())
        return ProductsViewModel(products, locations, shelves, search, store, restoreRepository)
    }

    @Test
    fun load_populates_products() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
            val vm = viewModel(products = repo)

            vm.load(householdId = 1, shelfId = 1)

            assertEquals(1, vm.state.value.products.size)
            assertEquals(
                "Peas",
                vm.state.value.products
                    .first()
                    .name,
            )
        }

    @Test
    fun increment_and_decrement_update_quantity_in_place() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)

            vm.increment(1)
            assertEquals(
                3,
                vm.state.value.products
                    .first()
                    .quantity,
            )
            vm.decrement(1)
            assertEquals(
                2,
                vm.state.value.products
                    .first()
                    .quantity,
            )
        }

    @Test
    fun create_adds_a_product_at_zero_quantity() =
        runTest {
            val vm = viewModel()
            vm.load(householdId = 1, shelfId = 1)
            vm.onNewNameChange("Bread")

            vm.create()

            assertTrue(
                vm.state.value.products
                    .any { it.name == "Bread" && it.quantity == 0 },
            )
            assertEquals("", vm.state.value.newName)
        }

    @Test
    fun scanning_a_known_code_increments_that_product() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Milk", 2, 1, code = "871234")) }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)

            vm.onBarcodeScanned("871234")

            assertEquals(
                3,
                vm.state.value.products
                    .first()
                    .quantity,
            )
            assertEquals(ScanResult.Incremented("Milk"), vm.state.value.scanResult)
            assertEquals(null, vm.state.value.pendingCode)
        }

    @Test
    fun scanning_an_unknown_code_attaches_it_to_the_next_create() =
        runTest {
            val repo = FakeProductRepository()
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)

            vm.onBarcodeScanned("999888")
            assertEquals(ScanResult.Unknown("999888"), vm.state.value.scanResult)

            vm.onNewNameChange("Oat milk")
            vm.create()

            assertEquals("999888", repo.items.first { it.name == "Oat milk" }.code)
            assertEquals(null, vm.state.value.pendingCode)
        }

    @Test
    fun start_move_lists_other_shelves_and_confirm_moves_the_product() =
        runTest {
            val products = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
            val locations = FakeLocationRepository(listOf(LocationDto(10, "Chest", "freezer")))
            // Shelves under location 10: current shelf 1 + a target shelf 2.
            val shelves =
                FakeShelfRepository(mapOf(10L to listOf(ShelfDto(1, "Top", 0, 10L), ShelfDto(2, "Bottom", 1, 10L))))
            val vm = viewModel(products, locations, shelves)
            vm.load(householdId = 1, shelfId = 1)

            vm.startMove(productId = 1)
            val targets = vm.state.value.moveTargets
            assertEquals(1, targets.size) // current shelf (1) excluded, only shelf 2 remains
            // Raw fields, not a pre-baked label: the ViewModel has no locale-correct
            // way to localize a system shelf's name itself (final review, ALSO FIX)
            // — the composable that renders this list does that at render time.
            assertEquals("Chest", targets.first().locationName)
            assertEquals("Bottom", targets.first().shelfName)
            assertFalse(targets.first().isSystemShelf)

            vm.confirmMove(targetShelfId = targets.first().shelfId)

            assertEquals(Triple(1L, 1L, 2L), products.lastMove)
            assertTrue(
                vm.state.value.products
                    .isEmpty(),
            ) // moved off this shelf
            assertEquals(null, vm.state.value.movingProductId)
        }

    @Test
    fun start_move_flags_the_unsorted_shelf_as_a_system_target() =
        runTest {
            // ALSO FIX (final review): the Unsorted shelf is a legitimate move target
            // (moving a product off its current shelf into the household's holding
            // shelf), so it must appear in this list — but MoveTarget must carry
            // isSystemShelf=true for it so the composable renders the LOCALIZED
            // "Unsorted" label instead of the server's raw literal name.
            val products = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
            val locations = FakeLocationRepository(listOf(LocationDto(10, "Chest", "freezer")))
            val shelves =
                FakeShelfRepository(
                    mapOf(
                        10L to
                            listOf(
                                ShelfDto(1, "Top", 0, 10L),
                                ShelfDto(2, "Unsorted", 1, 10L, is_system = true),
                            ),
                    ),
                )
            val vm = viewModel(products, locations, shelves)
            vm.load(householdId = 1, shelfId = 1)

            vm.startMove(productId = 1)

            val target =
                vm.state.value.moveTargets
                    .first { it.shelfId == 2L }
            assertTrue(target.isSystemShelf)
            assertEquals("Unsorted", target.shelfName)
        }

    @Test
    fun update_replaces_product_in_list() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Milk", 2, 1)) }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)

            vm.update(
                productId = 1,
                edit = ProductEdit("Oat Milk", "lactose free", null, isMandatory = true, lowStockThreshold = null),
            )

            val product =
                vm.state.value.products
                    .first { it.id == 1L }
            assertEquals("Oat Milk", product.name)
            assertTrue(product.is_mandatory == true)
        }

    @Test
    fun delete_optimistically_removes_product() =
        runTest {
            val repo =
                FakeProductRepository().apply {
                    items.add(ProductDto(1, "Milk", 2, 1))
                    items.add(ProductDto(2, "Butter", 1, 1))
                }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)
            assertEquals(2, vm.state.value.products.size)

            vm.delete(productId = 1)

            assertEquals(1, vm.state.value.products.size)
            assertEquals(
                "Butter",
                vm.state.value.products
                    .first()
                    .name,
            )
        }

    @Test
    fun delete_captures_the_server_minted_batch_id_for_undo() =
        runTest {
            // Product delete is the app's single most frequent destructive action —
            // this is what makes it Undo-able, mirroring Shelves/Storage/Drawer.
            // Unlike THOSE deletes, the batch id is server-minted (ProductController::
            // destroy), not client-minted, so this pins that the VM actually reads it
            // back off the repository call instead of discarding it.
            val repo =
                FakeProductRepository().apply {
                    items.add(ProductDto(1, "Milk", 2, 1))
                    deleteBatchId = "server-batch-42"
                }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)

            vm.delete(productId = 1)

            assertEquals("server-batch-42", vm.state.value.lastBatchId)
        }

    @Test
    fun a_failed_delete_does_not_set_a_batch_id() =
        runTest {
            val repo =
                FakeProductRepository().apply {
                    items.add(ProductDto(1, "Milk", 2, 1))
                    failDelete = true
                }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)

            vm.delete(productId = 1)

            assertNull(vm.state.value.lastBatchId)
            // The optimistic removal is reverted by the refresh() a failed delete
            // triggers, so the product is still there for the user to retry.
            assertEquals(1, vm.state.value.products.size)
        }

    @Test
    fun a_failed_delete_surfaces_its_error_instead_of_failing_silently() =
        runTest {
            // The bug: delete()'s failure branch set state.error, then immediately
            // called refresh() to revert the optimistic removal — but refresh()
            // routes through the shared launch() helper, which resets
            // error = null the instant its coroutine starts. The error the user
            // needed to see was wiped before it could ever be rendered, so a
            // failed delete failed SILENTLY. This must not regress.
            val repo =
                FakeProductRepository().apply {
                    items.add(ProductDto(1, "Milk", 2, 1))
                    failDelete = true
                }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)

            vm.delete(productId = 1)

            assertNotNull(vm.state.value.error)
        }

    @Test
    fun undo_delete_restores_the_batch_and_clears_it() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Milk", 2, 1)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(products = repo, restoreRepository = restore)
            vm.load(householdId = 1, shelfId = 1)
            vm.delete(productId = 1)
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
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Milk", 2, 1)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(products = repo, restoreRepository = restore)
            vm.load(householdId = 1, shelfId = 1)
            vm.delete(productId = 1)
            restore.fail = true

            vm.undoDelete()

            assertEquals(UndoOutcome.FAILURE, vm.state.value.undoResult)
            assertNull(vm.state.value.error)
            // The batch id survives a failed undo — nothing was actually restored.
            assertNotNull(vm.state.value.lastBatchId)
        }

    @Test
    fun consume_last_batch_clears_it_without_restoring() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Milk", 2, 1)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(products = repo, restoreRepository = restore)
            vm.load(householdId = 1, shelfId = 1)
            vm.delete(productId = 1)

            vm.consumeLastBatch()

            assertNull(vm.state.value.lastBatchId)
            assertEquals(0, restore.restoreCalls)
        }

    @Test
    fun consume_undo_result_clears_it() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Milk", 2, 1)) }
            val restore = FakeRestoreRepository()
            val vm = viewModel(products = repo, restoreRepository = restore)
            vm.load(householdId = 1, shelfId = 1)
            vm.delete(productId = 1)
            vm.undoDelete()
            assertNotNull(vm.state.value.undoResult)

            vm.consumeUndoResult()

            assertNull(vm.state.value.undoResult)
        }

    @Test
    fun cancel_move_clears_move_state() =
        runTest {
            val repo = FakeProductRepository().apply { items.add(ProductDto(1, "Peas", 2, 1)) }
            val locations = FakeLocationRepository(listOf(LocationDto(10, "Chest", "freezer")))
            val shelves =
                FakeShelfRepository(mapOf(10L to listOf(ShelfDto(1, "Top", 0, 10L), ShelfDto(2, "Bottom", 1, 10L))))
            val vm = viewModel(repo, locations, shelves)
            vm.load(householdId = 1, shelfId = 1)
            vm.startMove(productId = 1)
            assertTrue(vm.state.value.movingProductId != null)

            vm.cancelMove()

            assertEquals(null, vm.state.value.movingProductId)
            assertTrue(
                vm.state.value.moveTargets
                    .isEmpty(),
            )
        }

    @Test
    fun list_failure_surfaces_an_error() =
        runTest {
            val repo = FakeProductRepository().apply { failList = true }
            val vm = viewModel(products = repo)

            vm.load(householdId = 1, shelfId = 1)

            assertEquals("offline", vm.state.value.error)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun consumeError_clears_the_error_after_it_is_shown() =
        runTest {
            val repo = FakeProductRepository().apply { failList = true }
            val vm = viewModel(products = repo)
            vm.load(householdId = 1, shelfId = 1)
            assertEquals("offline", vm.state.value.error)

            // After the Snackbar has surfaced it, the error is consumed so it doesn't re-fire.
            vm.consumeError()
            assertNull(vm.state.value.error)
        }
}
