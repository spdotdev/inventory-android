package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class GoogleRequest(
    val id_token: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    val avatar_url: String? = null,
)

@Serializable
data class AuthResponse(
    val user: UserDto,
    val token: String,
)
