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

@Serializable
data class UpdateProductRequest(
    val name: String,
    val description: String? = null,
    val code: String? = null,
    val is_mandatory: Boolean = false,
)

@Serializable
data class AmountRequest(
    val amount: Int,
)

@Serializable
data class MoveProductRequest(
    val shelf_id: Long,
)
