package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShelfDto(
    val id: Long,
    val name: String,
    val position: Int,
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
