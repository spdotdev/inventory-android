package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MissingItemsCountResponse(
    val data: MissingItemsCountData,
)

@Serializable
data class MissingItemsCountData(
    val count: Int,
)
