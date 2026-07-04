package dev.scuttle.inventory.data.product

import android.net.Uri
import dev.scuttle.inventory.data.dto.ProductDto

interface ProductRepository {
    fun getCached(householdId: Long, shelfId: Long): List<ProductDto>?

    suspend fun list(householdId: Long, shelfId: Long): List<ProductDto>
    suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int): ProductDto
    suspend fun update(householdId: Long, shelfId: Long, productId: Long, name: String, description: String?, code: String?, isMandatory: Boolean): ProductDto
    suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto
    suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int): ProductDto
    suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long): ProductDto
    suspend fun uploadImage(householdId: Long, shelfId: Long, productId: Long, imageUri: Uri, mimeType: String): ProductDto
    suspend fun delete(householdId: Long, shelfId: Long, productId: Long)

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
