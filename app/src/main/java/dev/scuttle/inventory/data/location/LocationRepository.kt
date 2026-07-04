package dev.scuttle.inventory.data.location

import dev.scuttle.inventory.data.dto.LocationDto

interface LocationRepository {
    fun getCached(householdId: Long): List<LocationDto>?

    suspend fun list(householdId: Long): List<LocationDto>

    suspend fun create(householdId: Long, name: String, type: String): LocationDto

    suspend fun delete(householdId: Long, locationId: Long)

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
