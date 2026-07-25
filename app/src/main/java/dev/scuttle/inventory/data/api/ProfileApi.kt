package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.UpdateProfileRequest
import dev.scuttle.inventory.data.dto.UserResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface ProfileApi {
    @GET("me")
    suspend fun me(): UserResponse

    @PATCH("me")
    suspend fun update(
        @Body body: UpdateProfileRequest,
    ): UserResponse
}
