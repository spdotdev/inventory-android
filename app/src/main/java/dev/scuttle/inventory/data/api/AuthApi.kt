package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.AuthResponse
import dev.scuttle.inventory.data.dto.ForgotPasswordRequest
import dev.scuttle.inventory.data.dto.GoogleRequest
import dev.scuttle.inventory.data.dto.LoginRequest
import dev.scuttle.inventory.data.dto.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/google")
    suspend fun google(@Body body: GoogleRequest): AuthResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest)

    @POST("auth/logout")
    suspend fun logout()
}
