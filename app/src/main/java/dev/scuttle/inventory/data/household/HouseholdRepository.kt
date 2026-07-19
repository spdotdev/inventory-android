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

    /**
     * Downloads the household's JSON export. Default throws so test fakes only
     * implement it where a test actually exercises this (same pattern as
     * [update]'s default).
     */
    suspend fun export(householdId: Long): HouseholdExportFile =
        throw UnsupportedOperationException("export not supported")
}

/**
 * [suggestedFilename] comes from the server's Content-Disposition header when present.
 * A plain class, not a data class: nothing here is ever compared for equality, and a
 * data class over a ByteArray would generate a reference-equality equals()/hashCode()
 * that looks structural but isn't.
 */
class HouseholdExportFile(
    val bytes: ByteArray,
    val suggestedFilename: String,
)
