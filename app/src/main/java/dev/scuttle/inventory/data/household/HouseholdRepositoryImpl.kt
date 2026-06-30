package dev.scuttle.inventory.data.household

import dev.scuttle.inventory.data.api.HouseholdApi
import dev.scuttle.inventory.data.dto.CreateHouseholdRequest
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.JoinHouseholdRequest
import javax.inject.Inject

class HouseholdRepositoryImpl @Inject constructor(
    private val api: HouseholdApi,
) : HouseholdRepository {

    private var cache: List<HouseholdDto>? = null

    override fun getCached(): List<HouseholdDto>? = cache

    override suspend fun list(): List<HouseholdDto> = api.list().data.also { cache = it }

    override suspend fun create(name: String): HouseholdDto = api.create(CreateHouseholdRequest(name)).data

    override suspend fun join(code: String): HouseholdDto = api.join(JoinHouseholdRequest(code)).data

    override suspend fun leave(householdId: Long) {
        api.leave(householdId)
        cache = cache?.filter { it.id != householdId }
    }
}
