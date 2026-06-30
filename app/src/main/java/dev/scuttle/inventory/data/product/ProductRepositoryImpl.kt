package dev.scuttle.inventory.data.product

import dev.scuttle.inventory.data.api.ProductApi
import dev.scuttle.inventory.data.dto.AmountRequest
import dev.scuttle.inventory.data.dto.CreateProductRequest
import dev.scuttle.inventory.data.dto.MoveProductRequest
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.UpdateProductRequest
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val api: ProductApi,
) : ProductRepository {

    private val cache = mutableMapOf<Pair<Long, Long>, List<ProductDto>>()

    override fun getCached(householdId: Long, shelfId: Long): List<ProductDto>? =
        cache[householdId to shelfId]

    override suspend fun list(householdId: Long, shelfId: Long): List<ProductDto> =
        api.list(householdId, shelfId).data.also { cache[householdId to shelfId] = it }

    override suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int): ProductDto =
        api.create(householdId, shelfId, CreateProductRequest(name = name, quantity = quantity)).data.also { created ->
            val key = householdId to shelfId
            cache[key] = (cache[key] ?: emptyList()) + created
        }

    override suspend fun update(householdId: Long, shelfId: Long, productId: Long, name: String, description: String?, code: String?, isMandatory: Boolean): ProductDto =
        api.update(householdId, shelfId, productId, UpdateProductRequest(name, description, code, isMandatory)).data.also { updated ->
            cache.replaceProduct(householdId, shelfId, updated)
        }

    override suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
        api.add(householdId, shelfId, productId, AmountRequest(amount)).data.also { updated ->
            cache.replaceProduct(householdId, shelfId, updated)
        }

    override suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
        api.remove(householdId, shelfId, productId, AmountRequest(amount)).data.also { updated ->
            cache.replaceProduct(householdId, shelfId, updated)
        }

    override suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long): ProductDto =
        api.move(householdId, shelfId, productId, MoveProductRequest(targetShelfId)).data.also {
            cache[householdId to shelfId] = cache[householdId to shelfId]?.filter { p -> p.id != productId } ?: emptyList()
        }

    override suspend fun delete(householdId: Long, shelfId: Long, productId: Long) {
        api.delete(householdId, shelfId, productId)
        cache[householdId to shelfId] = cache[householdId to shelfId]?.filter { it.id != productId } ?: emptyList()
    }

    private fun MutableMap<Pair<Long, Long>, List<ProductDto>>.replaceProduct(householdId: Long, shelfId: Long, updated: ProductDto) {
        val key = householdId to shelfId
        this[key] = this[key]?.map { if (it.id == updated.id) updated else it } ?: listOf(updated)
    }
}
