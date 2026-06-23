package dev.scuttle.inventory.data.location

import dev.scuttle.inventory.data.dto.LocationDto

interface LocationRepository {
    suspend fun list(householdId: Long): List<LocationDto>

    suspend fun create(householdId: Long, name: String, type: String): LocationDto
}
