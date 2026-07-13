package dev.scuttle.inventory.data.shelf

import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.hierarchy.ShelfDeleteStrategy

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

    suspend fun delete(
        householdId: Long,
        locationId: Long,
        shelfId: Long,
    )

    /**
     * Defaults throw so test fakes only implement what a test actually exercises
     * (same pattern as [clear] and HouseholdRepository.updateTheme). Without
     * this, adding a method here breaks every fake in the unit-test suite.
     */
    suspend fun rename(
        householdId: Long,
        locationId: Long,
        shelfId: Long,
        name: String,
    ): ShelfDto = throw UnsupportedOperationException("rename not supported")

    suspend fun reorder(
        householdId: Long,
        locationId: Long,
        ids: List<Long>,
    ): List<ShelfDto> = throw UnsupportedOperationException("reorder not supported")

    suspend fun deleteWithStrategy(
        householdId: Long,
        locationId: Long,
        shelfId: Long,
        batchId: String,
        strategy: ShelfDeleteStrategy?,
        targetShelfId: Long?,
    ): Unit = throw UnsupportedOperationException("deleteWithStrategy not supported")

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
