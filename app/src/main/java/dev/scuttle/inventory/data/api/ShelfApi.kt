package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.CreateShelfRequest
import dev.scuttle.inventory.data.dto.ShelfListResponse
import dev.scuttle.inventory.data.dto.ShelfResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ShelfApi {
    @GET("households/{household}/locations/{location}/shelves")
    suspend fun list(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
    ): ShelfListResponse

    @POST("households/{household}/locations/{location}/shelves")
    suspend fun create(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Body body: CreateShelfRequest,
    ): ShelfResponse
}
