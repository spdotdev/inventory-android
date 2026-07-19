package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.CreateHouseholdRequest
import dev.scuttle.inventory.data.dto.DeleteHouseholdRequest
import dev.scuttle.inventory.data.dto.HouseholdListResponse
import dev.scuttle.inventory.data.dto.HouseholdResponse
import dev.scuttle.inventory.data.dto.JoinHouseholdRequest
import dev.scuttle.inventory.data.dto.UpdateHouseholdRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface HouseholdApi {
    @PATCH("households/{household}")
    suspend fun update(
        @Path("household") householdId: Long,
        @Body body: UpdateHouseholdRequest,
    ): HouseholdResponse

    @GET("households")
    suspend fun list(): HouseholdListResponse

    @POST("households")
    suspend fun create(
        @Body body: CreateHouseholdRequest,
    ): HouseholdResponse

    @POST("households/join")
    suspend fun join(
        @Body body: JoinHouseholdRequest,
    ): HouseholdResponse

    @DELETE("households/{household}/leave")
    suspend fun leave(
        @Path("household") householdId: Long,
    )

    // @DELETE cannot carry a body; @HTTP(hasBody = true) can. The server verifies
    // the household's exact name as a typed confirmation (422 on mismatch, 403
    // non-owner) — same pattern as ShelfApi/LocationApi's delete.
    @HTTP(method = "DELETE", path = "households/{household}", hasBody = true)
    suspend fun delete(
        @Path("household") householdId: Long,
        @Body body: DeleteHouseholdRequest,
    )

    // @Streaming + the raw Response so the caller can read the Content-Disposition
    // header for the server's exact filename, and buffer the body itself rather than
    // Retrofit/OkHttp doing it eagerly — the export is a one-shot user action, not
    // something loaded on every screen visit.
    @Streaming
    @GET("households/{household}/export")
    suspend fun export(
        @Path("household") householdId: Long,
    ): Response<ResponseBody>
}
