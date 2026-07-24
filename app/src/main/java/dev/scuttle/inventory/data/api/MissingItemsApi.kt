package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.MissingItemsCountResponse
import retrofit2.http.GET

interface MissingItemsApi {
    @GET("missing-items/count")
    suspend fun count(): MissingItemsCountResponse
}
