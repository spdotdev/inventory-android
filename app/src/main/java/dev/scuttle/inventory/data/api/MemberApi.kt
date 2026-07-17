package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.MemberListResponse
import dev.scuttle.inventory.data.dto.MemberResponse
import dev.scuttle.inventory.data.dto.TransferOwnershipRequest
import dev.scuttle.inventory.data.dto.UpdateMemberRoleRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface MemberApi {
    @GET("households/{household}/members")
    suspend fun list(
        @Path("household") householdId: Long,
    ): MemberListResponse

    @PATCH("households/{household}/members/{user}")
    suspend fun updateRole(
        @Path("household") householdId: Long,
        @Path("user") userId: Long,
        @Body body: UpdateMemberRoleRequest,
    ): MemberResponse

    @DELETE("households/{household}/members/{user}")
    suspend fun remove(
        @Path("household") householdId: Long,
        @Path("user") userId: Long,
    )

    @POST("households/{household}/transfer-ownership")
    suspend fun transferOwnership(
        @Path("household") householdId: Long,
        @Body body: TransferOwnershipRequest,
    )
}
