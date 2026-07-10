package dev.scuttle.inventory.data.household

import dev.scuttle.inventory.data.dto.HouseholdDto

interface HouseholdRepository {
    fun getCached(): List<HouseholdDto>?

    suspend fun list(): List<HouseholdDto>

    suspend fun create(name: String): HouseholdDto

    suspend fun join(code: String): HouseholdDto

    suspend fun leave(householdId: Long)

    /**
     * Set (or clear, with nulls) the user-chosen theme keys. Default throws so
     * test fakes only implement it where a test actually exercises theming
     * (same pattern as [clear]'s no-op default).
     */
    suspend fun updateTheme(
        householdId: Long,
        color: String?,
        icon: String?,
    ): HouseholdDto = throw UnsupportedOperationException("updateTheme not supported")

    /** Drop the in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
