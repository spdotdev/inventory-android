package dev.scuttle.inventory.data.hierarchy

import dev.scuttle.inventory.data.dto.DeletedBatchDto

interface RestoreRepository {
    /** Restores everything tagged with the given deletion batch id. Returns the count restored. */
    suspend fun restore(
        householdId: Long,
        batchId: String,
    ): Int

    /**
     * Lists restorable batches, newest first — the recently-deleted browser's data
     * source. Default throws so test fakes only implement it where a test actually
     * exercises this (same pattern as HouseholdRepository's optional methods).
     */
    suspend fun listDeleted(householdId: Long): List<DeletedBatchDto> =
        throw UnsupportedOperationException("listDeleted not supported")
}
