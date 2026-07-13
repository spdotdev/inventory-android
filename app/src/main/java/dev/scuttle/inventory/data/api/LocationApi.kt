package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.CreateLocationRequest
import dev.scuttle.inventory.data.dto.DeleteLocationRequest
import dev.scuttle.inventory.data.dto.LocationListResponse
import dev.scuttle.inventory.data.dto.LocationResponse
import dev.scuttle.inventory.data.dto.ReorderRequest
import dev.scuttle.inventory.data.dto.UpdateLocationRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface LocationApi {
    @GET("households/{household}/locations")
    suspend fun list(
        @Path("household") householdId: Long,
    ): LocationListResponse

    @POST("households/{household}/locations")
    suspend fun create(
        @Path("household") householdId: Long,
        @Body body: CreateLocationRequest,
    ): LocationResponse

    @PATCH("households/{household}/locations/{location}")
    suspend fun update(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Body body: UpdateLocationRequest,
    ): LocationResponse

    @PATCH("households/{household}/locations/reorder")
    suspend fun reorder(
        @Path("household") householdId: Long,
        @Body body: ReorderRequest,
    ): LocationListResponse

    @DELETE("households/{household}/locations/{location}")
    suspend fun delete(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
    )

    // @DELETE cannot carry a body; @HTTP(hasBody = true) can. The strategy and
    // the batch id have to travel with the request.
    @HTTP(method = "DELETE", path = "households/{household}/locations/{location}", hasBody = true)
    suspend fun delete(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Body body: DeleteLocationRequest,
    )
}
