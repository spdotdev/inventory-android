package dev.scuttle.inventory.data.location

import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy

interface LocationRepository {
    fun getCached(householdId: Long): List<LocationDto>?

    suspend fun list(householdId: Long): List<LocationDto>

    suspend fun create(
        householdId: Long,
        name: String,
        type: String,
    ): LocationDto

    suspend fun delete(
        householdId: Long,
        locationId: Long,
    )

    /**
     * Defaults throw so test fakes only implement what a test actually exercises
     * (same pattern as [clear] and HouseholdRepository.updateTheme). Without
     * this, adding a method here breaks every fake in the unit-test suite.
     */
    suspend fun rename(
        householdId: Long,
        locationId: Long,
        name: String,
        type: String,
    ): LocationDto = throw UnsupportedOperationException("rename not supported")

    suspend fun reorder(
        householdId: Long,
        ids: List<Long>,
    ): List<LocationDto> = throw UnsupportedOperationException("reorder not supported")

    suspend fun deleteWithStrategy(
        householdId: Long,
        locationId: Long,
        batchId: String,
        strategy: LocationDeleteStrategy?,
        targetLocationId: Long?,
    ): Unit = throw UnsupportedOperationException("deleteWithStrategy not supported")

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
