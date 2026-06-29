package dev.scuttle.inventory.data.product

import dev.scuttle.inventory.data.dto.ProductDto

interface ProductRepository {
    suspend fun list(householdId: Long, shelfId: Long): List<ProductDto>
    suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int): ProductDto
    suspend fun update(householdId: Long, shelfId: Long, productId: Long, name: String, description: String?, code: String?, isMandatory: Boolean): ProductDto
    suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto
    suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto
    suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long): ProductDto
    suspend fun delete(householdId: Long, shelfId: Long, productId: Long)
}
