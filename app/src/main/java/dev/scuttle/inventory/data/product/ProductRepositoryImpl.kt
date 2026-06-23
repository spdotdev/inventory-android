package dev.scuttle.inventory.data.product

import dev.scuttle.inventory.data.api.ProductApi
import dev.scuttle.inventory.data.dto.AmountRequest
import dev.scuttle.inventory.data.dto.CreateProductRequest
import dev.scuttle.inventory.data.dto.MoveProductRequest
import dev.scuttle.inventory.data.dto.ProductDto
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val api: ProductApi,
) : ProductRepository {

    override suspend fun list(householdId: Long, shelfId: Long): List<ProductDto> =
        api.list(householdId, shelfId).data

    override suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int): ProductDto =
        api.create(householdId, shelfId, CreateProductRequest(name = name, quantity = quantity)).data

    override suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
        api.add(householdId, shelfId, productId, AmountRequest(amount)).data

    override suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto =
        api.remove(householdId, shelfId, productId, AmountRequest(amount)).data

    override suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long): ProductDto =
        api.move(householdId, shelfId, productId, MoveProductRequest(targetShelfId)).data
}
