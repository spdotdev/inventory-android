package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.CreateHouseholdRequest
import dev.scuttle.inventory.data.dto.HouseholdListResponse
import dev.scuttle.inventory.data.dto.HouseholdResponse
import dev.scuttle.inventory.data.dto.JoinHouseholdRequest
import dev.scuttle.inventory.data.dto.UpdateHouseholdThemeRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface HouseholdApi {
    @PATCH("households/{household}")
    suspend fun updateTheme(
        @Path("household") householdId: Long,
        @Body body: UpdateHouseholdThemeRequest,
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
}
