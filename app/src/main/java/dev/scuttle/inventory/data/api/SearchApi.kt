package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.SearchListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SearchApi {
    @GET("households/{household}/search")
    suspend fun search(
        @Path("household") householdId: Long,
        @Query("q") query: String,
    ): SearchListResponse
}
