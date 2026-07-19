package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.DeletedBatchListResponse
import dev.scuttle.inventory.data.dto.RestoreResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RestoreApi {
    @POST("households/{household}/restore/{batch}")
    suspend fun restore(
        @Path("household") householdId: Long,
        @Path("batch") batchId: String,
    ): RestoreResponse

    @GET("households/{household}/deleted")
    suspend fun listDeleted(
        @Path("household") householdId: Long,
    ): DeletedBatchListResponse
}
