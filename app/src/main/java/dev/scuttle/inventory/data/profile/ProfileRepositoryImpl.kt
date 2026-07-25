package dev.scuttle.inventory.data.profile

import dev.scuttle.inventory.data.api.ProfileApi
import dev.scuttle.inventory.data.dto.UpdateProfileRequest
import dev.scuttle.inventory.data.dto.UserDto
import javax.inject.Inject

class ProfileRepositoryImpl
    @Inject
    constructor(
        private val api: ProfileApi,
    ) : ProfileRepository {
        override suspend fun me(): UserDto = api.me().data

        override suspend fun update(
            name: String,
            email: String,
            gender: String?,
        ): UserDto = api.update(UpdateProfileRequest(name, email, gender)).data
    }
