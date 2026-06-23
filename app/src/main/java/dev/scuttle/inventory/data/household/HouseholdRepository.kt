package dev.scuttle.inventory.data.household

import dev.scuttle.inventory.data.dto.HouseholdDto

interface HouseholdRepository {
    suspend fun list(): List<HouseholdDto>

    suspend fun create(name: String): HouseholdDto

    suspend fun join(code: String): HouseholdDto
}
