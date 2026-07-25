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
    val gender: String? = null,
    val avatar_url: String? = null,
)

@Serializable
data class AuthResponse(
    val user: UserDto,
    val token: String,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

// Laravel API Resources wrap payloads in a `data` envelope.
@Serializable
data class UserResponse(
    val data: UserDto,
)

@Serializable
data class UpdateProfileRequest(
    val name: String,
    val email: String,
    // Deliberately NO default: this app's Json runs with explicitNulls = true /
    // encodeDefaults = false, so a property with no default is always encoded, even
    // null — required here so clearing gender back to "unset" actually sends
    // `"gender":null` instead of the key being omitted (see the Android CLAUDE.md's
    // asymmetric-nullable note on UpdateHouseholdRequest for the same pattern).
    val gender: String?,
)
