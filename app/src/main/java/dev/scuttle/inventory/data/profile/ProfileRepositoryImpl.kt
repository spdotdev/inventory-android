package dev.scuttle.inventory.data.profile

import dev.scuttle.inventory.data.api.ProfileApi
import dev.scuttle.inventory.data.dto.UserDto
import javax.inject.Inject

class ProfileRepositoryImpl
    @Inject
    constructor(
        private val api: ProfileApi,
    ) : ProfileRepository {
        override suspend fun me(): UserDto = api.me().data
    }
