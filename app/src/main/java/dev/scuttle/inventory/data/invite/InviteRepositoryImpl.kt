package dev.scuttle.inventory.data.invite

import dev.scuttle.inventory.data.api.InviteApi
import dev.scuttle.inventory.data.dto.InviteResponse
import javax.inject.Inject

class InviteRepositoryImpl @Inject constructor(
    private val api: InviteApi,
) : InviteRepository {

    override suspend fun invite(householdId: Long): InviteResponse = api.invite(householdId)
}
