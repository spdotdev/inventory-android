package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.CreateLocationRequest
import dev.scuttle.inventory.data.dto.LocationListResponse
import dev.scuttle.inventory.data.dto.LocationResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface LocationApi {
    @GET("households/{household}/locations")
    suspend fun list(@Path("household") householdId: Long): LocationListResponse

    @POST("households/{household}/locations")
    suspend fun create(
        @Path("household") householdId: Long,
        @Body body: CreateLocationRequest,
    ): LocationResponse
}
