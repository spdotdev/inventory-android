package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.UserResponse
import retrofit2.http.GET

interface ProfileApi {
    @GET("me")
    suspend fun me(): UserResponse
}
