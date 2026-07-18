// DTO property names mirror the API's snake_case wire format (repo-wide convention).
@file:Suppress("ConstructorParameterNaming")

package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: Long,
    val name: String,
    val quantity: Int,
    val shelf_id: Long,
    val description: String? = null,
    val code: String? = null,
    val is_mandatory: Boolean? = false,
    // Starred marker (a MARKER, never a sort key — see HierarchyOrder).
    val is_starred: Boolean? = false,
    val image_url: String? = null,
    // "Running low" at quantity <= threshold; null = feature off for this product.
    val low_stock_threshold: Int? = null,
)

@Serializable
data class ProductListResponse(
    val data: List<ProductDto>,
)

@Serializable
data class ProductResponse(
    val data: ProductDto,
)

/**
 * ProductController::destroy() mints a batch-of-one `deletion_batch_id` for a
 * solo product delete (unlike shelf/location deletes, the CLIENT never mints
 * this one) and returns it specifically so the client can offer Undo. The
 * wire key stays snake_case — match it exactly, this project has already
 * shipped one Critical from a serialization mismatch.
 */
@Serializable
data class ProductDeleteResponse(
    val message: String,
    val deletion_batch_id: String,
)

@Serializable
data class CreateProductRequest(
    val name: String,
    val quantity: Int,
    // Scanned/entered product code; omitted when null (create-without-code).
    val code: String? = null,
)

// No property defaults on purpose: the app's Json has encodeDefaults=false, so a
// property equal to its default is OMITTED from the PATCH body — and the API's
// `sometimes` rules then keep the old value. With no defaults every field is always
// encoded (null encodes as an explicit JSON null), so clearing description/code,
// unchecking is_mandatory, and clearing low_stock_threshold actually persist.
@Serializable
data class UpdateProductRequest(
    val name: String,
    val description: String?,
    val code: String?,
    val is_mandatory: Boolean,
    val is_starred: Boolean,
    val low_stock_threshold: Int?,
)

@Serializable
data class AmountRequest(
    val amount: Int,
)

@Serializable
data class MoveProductRequest(
    val shelf_id: Long,
)
