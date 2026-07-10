package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.InviteResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface InviteApi {
    @GET("households/{household}/invite")
    suspend fun invite(
        @Path("household") householdId: Long,
    ): InviteResponse
}
