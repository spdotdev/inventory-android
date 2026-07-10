package dev.scuttle.inventory

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.ui.products.ProductDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
        ): ProductDto = items.first { it.id == productId }

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ): ProductDto = items.first { it.id == productId }

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
        ) {
            if (failDelete) throw RuntimeException("delete failed")
            items.removeIf { it.id == productId }
        }

        override suspend fun uploadImage(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            imageUri: Uri,
            mimeType: String,
        ): ProductDto = items.first { it.id == productId }
    }

    @Test
    fun load_finds_product_by_id() =
        runTest {
            val product = ProductDto(id = 42, name = "Milk", quantity = 2, shelf_id = 1)
            val vm = ProductDetailViewModel(savedState(productId = 42), FakeProductRepository(listOf(product)))

            assertEquals("Milk", vm.state.value.product?.name)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun load_sets_null_when_product_not_found() =
        runTest {
            val vm = ProductDetailViewModel(savedState(productId = 99), FakeProductRepository())

            assertNull(vm.state.value.product)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun load_failure_surfaces_error() =
        runTest {
            val repo = FakeProductRepository().apply { failList = true }
            val vm = ProductDetailViewModel(savedState(), repo)

            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun save_updates_product_and_sets_saved() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val vm = ProductDetailViewModel(savedState(), FakeProductRepository(listOf(product)))

            vm.save(ProductEdit("Oat Milk", "lactose free", null, isMandatory = true, lowStockThreshold = null))

            assertTrue(vm.state.value.saved)
            assertEquals("Oat Milk", vm.state.value.product?.name)
            assertTrue(vm.state.value.product?.is_mandatory == true)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun save_failure_surfaces_error_and_does_not_set_saved() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failUpdate = true }
            val vm = ProductDetailViewModel(savedState(), repo)

            vm.save(ProductEdit("Oat Milk", null, null, false, null))

            assertFalse(vm.state.value.saved)
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun save_persists_mandatory_flag() =
        runTest {
            val product = ProductDto(id = 1, name = "Eggs", quantity = 0, shelf_id = 1, is_mandatory = false)
            val repo = FakeProductRepository(listOf(product))
            val vm = ProductDetailViewModel(savedState(), repo)

            vm.save(ProductEdit("Eggs", null, null, isMandatory = true, lowStockThreshold = null))

            assertTrue(repo.items.first().is_mandatory == true)
        }

    @Test
    fun delete_removes_product_and_sets_deleted() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product))
            val vm = ProductDetailViewModel(savedState(), repo)

            vm.delete()

            assertTrue(vm.state.value.deleted)
            assertTrue(repo.items.isEmpty())
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun delete_failure_surfaces_error() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failDelete = true }
            val vm = ProductDetailViewModel(savedState(), repo)

            vm.delete()

            assertFalse(vm.state.value.deleted)
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun consumeError_clears_the_one_shot_error_after_it_is_shown() =
        runTest {
            val product = ProductDto(id = 1, name = "Milk", quantity = 2, shelf_id = 1)
            val repo = FakeProductRepository(listOf(product)).apply { failUpdate = true }
            val vm = ProductDetailViewModel(savedState(), repo)

            vm.save(ProductEdit("Oat Milk", null, null, false, null))
            assertNotNull(vm.state.value.error)

            // After the Snackbar has shown it, the error is consumed so it doesn't re-fire.
            vm.consumeError()
            assertNull(vm.state.value.error)
        }
}
