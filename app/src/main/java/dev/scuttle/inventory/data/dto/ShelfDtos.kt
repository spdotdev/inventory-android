package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShelfDto(
    val id: Long,
    val name: String,
    val position: Int? = null,
    val location_id: Long,
)

@Serializable
data class ShelfListResponse(
    val data: List<ShelfDto>,
)

@Serializable
data class ShelfResponse(
    val data: ShelfDto,
)

@Serializable
data class CreateShelfRequest(
    val name: String,
)
