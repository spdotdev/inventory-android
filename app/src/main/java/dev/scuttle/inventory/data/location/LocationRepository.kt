package dev.scuttle.inventory.data.location

import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.hierarchy.LocationDeletion

interface LocationRepository {
    fun getCached(householdId: Long): List<LocationDto>?

    suspend fun list(householdId: Long): List<LocationDto>

    suspend fun create(
        householdId: Long,
        name: String,
        type: String,
    ): LocationDto

    /**
     * Update a location's name, type, and/or theme keys (null = clear back to
     * the derived default) — same shape as ShelfRepository.update. Default
     * throws so test fakes only implement it where a test actually exercises
     * this (same pattern as [clear]'s no-op default). Without this, adding a
     * method here breaks every fake in the unit-test suite.
     */
    suspend fun update(
        householdId: Long,
        locationId: Long,
        name: String?,
        type: String?,
        color: String?,
        icon: String?,
    ): LocationDto = throw UnsupportedOperationException("update not supported")

    suspend fun reorder(
        householdId: Long,
        ids: List<Long>,
    ): List<LocationDto> = throw UnsupportedOperationException("reorder not supported")

    suspend fun deleteWithStrategy(
        householdId: Long,
        locationId: Long,
        deletion: LocationDeletion,
    ): Unit = throw UnsupportedOperationException("deleteWithStrategy not supported")

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
