package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.CreateHouseholdRequest
import dev.scuttle.inventory.data.dto.HouseholdListResponse
import dev.scuttle.inventory.data.dto.HouseholdResponse
import dev.scuttle.inventory.data.dto.JoinHouseholdRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface HouseholdApi {
    @GET("households")
    suspend fun list(): HouseholdListResponse

    @POST("households")
    suspend fun create(@Body body: CreateHouseholdRequest): HouseholdResponse

    @POST("households/join")
    suspend fun join(@Body body: JoinHouseholdRequest): HouseholdResponse
}
