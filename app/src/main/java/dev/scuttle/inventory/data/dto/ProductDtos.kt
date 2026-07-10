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

@Serializable
data class CreateProductRequest(
    val name: String,
    val quantity: Int,
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
