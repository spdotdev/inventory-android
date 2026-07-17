package dev.scuttle.inventory.data.member

import dev.scuttle.inventory.data.dto.MemberDto

interface MemberRepository {
    suspend fun list(householdId: Long): List<MemberDto>

    suspend fun updateRole(
        householdId: Long,
        userId: Long,
        role: String,
    ): MemberDto

    suspend fun remove(
        householdId: Long,
        userId: Long,
    )

    suspend fun transferOwnership(
        householdId: Long,
        userId: Long,
    )

    /** Drop any in-memory cache so one account's data never bleeds into the next session. */
    fun clear() {}
}
