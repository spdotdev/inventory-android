package dev.scuttle.inventory.data.household

import dev.scuttle.inventory.data.dto.HouseholdDto

interface HouseholdRepository {
    fun getCached(): List<HouseholdDto>?

    suspend fun list(): List<HouseholdDto>

    suspend fun create(name: String): HouseholdDto

    suspend fun join(code: String): HouseholdDto

    suspend fun leave(householdId: Long)

    /**
     * Permanently delete a household — owner-only; the server verifies
     * [nameConfirmation] against the household's exact current name (422 on
     * mismatch, 403 non-owner). Default throws so test fakes only implement it
     * where a test actually exercises this (same pattern as [update]'s default).
     */
    suspend fun delete(
        householdId: Long,
        nameConfirmation: String,
    ): Unit = throw UnsupportedOperationException("delete not supported")

    /**
     * Update the household's name and/or theme keys (null = clear back to the
     * derived default). Default throws so test fakes only implement it where a
     * test actually exercises this (same pattern as [clear]'s no-op default).
     */
    suspend fun update(
        householdId: Long,
        name: String?,
        color: String?,
        icon: String?,
    ): HouseholdDto = throw UnsupportedOperationException("update not supported")

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
