package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.LowStockCountResponse
import retrofit2.http.GET

interface LowStockApi {
    @GET("low-stock/count")
    suspend fun count(): LowStockCountResponse
}
