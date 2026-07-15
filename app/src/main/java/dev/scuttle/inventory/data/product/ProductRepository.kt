package dev.scuttle.inventory.data.product

import android.net.Uri
import dev.scuttle.inventory.data.dto.ProductDto

/** Editable product fields sent by PATCH — grouped so callers can't misorder them. */
data class ProductEdit(
    val name: String,
    val description: String?,
    val code: String?,
    val isMandatory: Boolean,
    val lowStockThreshold: Int?,
)

interface ProductRepository {
    fun getCached(
        householdId: Long,
        shelfId: Long,
    ): List<ProductDto>?

    suspend fun list(
        householdId: Long,
        shelfId: Long,
    ): List<ProductDto>

    suspend fun create(
        householdId: Long,
        shelfId: Long,
        name: String,
        quantity: Int,
        code: String? = null,
    ): ProductDto

    suspend fun update(
        householdId: Long,
        shelfId: Long,
        productId: Long,
        edit: ProductEdit,
    ): ProductDto

    suspend fun add(
        householdId: Long,
        shelfId: Long,
        productId: Long,
        amount: Int,
    ): ProductDto

    suspend fun remove(
        householdId: Long,
        shelfId: Long,
        productId: Long,
        amount: Int,
    ): ProductDto

    suspend fun move(
        householdId: Long,
        shelfId: Long,
        productId: Long,
        targetShelfId: Long,
    ): ProductDto

    suspend fun uploadImage(
        householdId: Long,
        shelfId: Long,
        productId: Long,
        imageUri: Uri,
        mimeType: String,
    ): ProductDto

    /**
     * Deletes the product and returns the server-minted `deletion_batch_id`
     * (see [dev.scuttle.inventory.data.dto.ProductDeleteResponse]) so the
     * caller can offer Undo via [dev.scuttle.inventory.data.hierarchy.RestoreRepository] —
     * unlike a shelf/location delete, the CLIENT never mints this batch id itself.
     */
    suspend fun delete(
        householdId: Long,
        shelfId: Long,
        productId: Long,
    ): String

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
