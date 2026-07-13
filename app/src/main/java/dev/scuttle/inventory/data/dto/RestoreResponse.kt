package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RestoreResponse(
    val message: String,
    val restored: Int,
)
