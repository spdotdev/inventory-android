package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LowStockCountResponse(
    val data: LowStockCountData,
)

@Serializable
data class LowStockCountData(
    val count: Int,
)
