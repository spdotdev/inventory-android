package dev.scuttle.inventory.data.shelf

import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.hierarchy.ShelfDeletion

interface ShelfRepository {
    fun getCached(
        householdId: Long,
        locationId: Long,
    ): List<ShelfDto>?

    suspend fun list(
        householdId: Long,
        locationId: Long,
    ): List<ShelfDto>

    suspend fun create(
        householdId: Long,
        locationId: Long,
        name: String,
    ): ShelfDto

    /**
     * Update a shelf's name and/or theme keys (null = clear back to the derived
     * default) — same shape as HouseholdRepository.update. Default throws so test
     * fakes only implement it where a test actually exercises this (same pattern
     * as [clear]'s no-op default). Without this, adding a method here breaks
     * every fake in the unit-test suite.
     */
    suspend fun update(
        householdId: Long,
        locationId: Long,
        shelfId: Long,
        name: String?,
        color: String?,
        icon: String?,
    ): ShelfDto = throw UnsupportedOperationException("update not supported")

    suspend fun reorder(
        householdId: Long,
        locationId: Long,
        ids: List<Long>,
    ): List<ShelfDto> = throw UnsupportedOperationException("reorder not supported")

    suspend fun deleteWithStrategy(
        householdId: Long,
        locationId: Long,
        shelfId: Long,
        deletion: ShelfDeletion,
    ): Unit = throw UnsupportedOperationException("deleteWithStrategy not supported")

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
