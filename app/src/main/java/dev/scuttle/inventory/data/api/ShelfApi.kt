package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.CreateShelfRequest
import dev.scuttle.inventory.data.dto.DeleteShelfRequest
import dev.scuttle.inventory.data.dto.ReorderRequest
import dev.scuttle.inventory.data.dto.ShelfListResponse
import dev.scuttle.inventory.data.dto.ShelfResponse
import dev.scuttle.inventory.data.dto.UpdateShelfRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
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

    @PATCH("households/{household}/locations/{location}/shelves/{shelf}")
    suspend fun update(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Path("shelf") shelfId: Long,
        @Body body: UpdateShelfRequest,
    ): ShelfResponse

    @PATCH("households/{household}/locations/{location}/shelves/reorder")
    suspend fun reorder(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Body body: ReorderRequest,
    ): ShelfListResponse

    // @DELETE cannot carry a body; @HTTP(hasBody = true) can. The strategy and
    // the batch id have to travel with the request.
    @HTTP(method = "DELETE", path = "households/{household}/locations/{location}/shelves/{shelf}", hasBody = true)
    suspend fun delete(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Path("shelf") shelfId: Long,
        @Body body: DeleteShelfRequest,
    )
}
