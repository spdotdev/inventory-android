package dev.scuttle.inventory

import android.content.ContentResolver
import dev.scuttle.inventory.data.api.ProductApi
import dev.scuttle.inventory.data.dto.AmountRequest
import dev.scuttle.inventory.data.dto.CreateProductRequest
import dev.scuttle.inventory.data.dto.MoveProductRequest
import dev.scuttle.inventory.data.dto.ProductDeleteResponse
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ProductListResponse
import dev.scuttle.inventory.data.dto.ProductResponse
import dev.scuttle.inventory.data.dto.UpdateProductRequest
import dev.scuttle.inventory.data.product.ProductRepositoryImpl
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * IMPORTANT fix regression: move() left the destination shelf's cache entry
 * untouched, so a subsequent load() of that shelf (getCached() returning its
 * now-stale list) never showed the moved product. Unlike every other repo in
 * this codebase, ProductRepositoryImpl is exercised directly here (rather than
 * only indirectly through a hand-written ProductRepository fake in a VM test)
 * because the bug is a cache-bookkeeping detail internal to THIS class — a
 * fake reimplementing the same fix would only prove the fake correct, not the
 * production code (see CLAUDE.md's "a fake that lies about the server" lesson,
 * which cuts both ways: a fake that DUPLICATES the fix under test is just as
 * useless as one that lies about it). ContentResolver is never touched by
 * move(), so an unused stub subclass is enough to satisfy the constructor.
 */
class ProductRepositoryImplTest {
    private class FakeProductApi : ProductApi {
        var moveResponse: ProductDto? = null
        val listResponses = mutableMapOf<Pair<Long, Long>, List<ProductDto>>()

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ): ProductListResponse = ProductListResponse(listResponses[householdId to shelfId].orEmpty())

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            body: CreateProductRequest,
        ): ProductResponse = throw NotImplementedError()

        override suspend fun update(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            body: UpdateProductRequest,
        ): ProductResponse = throw NotImplementedError()

        override suspend fun add(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            body: AmountRequest,
        ): ProductResponse = throw NotImplementedError()

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            body: AmountRequest,
        ): ProductResponse = throw NotImplementedError()

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            body: MoveProductRequest,
        ): ProductResponse = ProductResponse(moveResponse!!)

        override suspend fun uploadImage(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            image: MultipartBody.Part,
        ): ProductResponse = throw NotImplementedError()

        override suspend fun delete(
            householdId: Long,
            shelfId: Long,
            productId: Long,
        ): ProductDeleteResponse = throw NotImplementedError()
    }

    private fun repository(api: FakeProductApi): ProductRepositoryImpl {
        val resolver: ContentResolver = object : ContentResolver(null) {}
        return ProductRepositoryImpl(api, resolver)
    }

    @Test
    fun move_invalidates_the_destination_shelfs_cache_so_the_next_load_refetches() =
        runTest {
            val api = FakeProductApi()
            val repo = repository(api)
            // The destination shelf (20) was already visited this session — its
            // cache holds a product that was on it BEFORE the move.
            repo.primeCacheViaList(
                api,
                householdId = 1,
                shelfId = 20,
                products = listOf(ProductDto(2, "Butter", 1, 20)),
            )
            // ProductController::move returns the moved product with its NEW
            // shelf_id already set (see the Laravel controller: `$product->
            // shelf_id = $target->getKey(); $product->save(); return new
            // ProductResource($product);`).
            api.moveResponse = ProductDto(1, "Milk", 1, shelf_id = 20)

            repo.move(householdId = 1, shelfId = 10, productId = 1, targetShelfId = 20)

            // Invalidated, not silently left stale and not blindly appended (a
            // partial "list of 1" would be just as dishonest as the stale list it
            // replaces) — the next load() must go back to the network.
            assertNull(repo.getCached(householdId = 1, shelfId = 20))
        }

    @Test
    fun move_removes_the_product_from_the_source_shelfs_cache() =
        runTest {
            val api = FakeProductApi()
            val repo = repository(api)
            repo.primeCacheViaList(
                api,
                householdId = 1,
                shelfId = 10,
                products = listOf(ProductDto(1, "Milk", 1, 10), ProductDto(3, "Eggs", 6, 10)),
            )
            api.moveResponse = ProductDto(1, "Milk", 1, shelf_id = 20)

            repo.move(householdId = 1, shelfId = 10, productId = 1, targetShelfId = 20)

            val remaining = repo.getCached(householdId = 1, shelfId = 10)
            assertEquals(listOf(3L), remaining?.map { it.id })
        }

    /** Seeds [ProductRepositoryImpl]'s cache the same way production does: via list(). */
    private suspend fun ProductRepositoryImpl.primeCacheViaList(
        api: FakeProductApi,
        householdId: Long,
        shelfId: Long,
        products: List<ProductDto>,
    ) {
        api.listResponses[householdId to shelfId] = products
        list(householdId, shelfId)
    }
}
