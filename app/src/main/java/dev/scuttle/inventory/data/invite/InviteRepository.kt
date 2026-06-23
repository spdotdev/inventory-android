package dev.scuttle.inventory.data.invite

import dev.scuttle.inventory.data.dto.InviteResponse

interface InviteRepository {
    suspend fun invite(householdId: Long): InviteResponse
}
