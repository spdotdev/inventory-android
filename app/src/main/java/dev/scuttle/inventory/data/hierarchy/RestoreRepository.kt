package dev.scuttle.inventory.data.hierarchy

interface RestoreRepository {
    /** Restores everything tagged with the given deletion batch id. Returns the count restored. */
    suspend fun restore(
        householdId: Long,
        batchId: String,
    ): Int
}
