package dev.scuttle.inventory.data.profile

import dev.scuttle.inventory.data.dto.UserDto

interface ProfileRepository {
    suspend fun me(): UserDto

    suspend fun update(
        name: String,
        email: String,
        gender: String?,
    ): UserDto
}
