package dev.scuttle.inventory.data.household

import dev.scuttle.inventory.data.api.HouseholdApi
import dev.scuttle.inventory.data.dto.CreateHouseholdRequest
import dev.scuttle.inventory.data.dto.DeleteHouseholdRequest
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.JoinHouseholdRequest
import dev.scuttle.inventory.data.dto.UpdateHouseholdRequest
import javax.inject.Inject

class HouseholdRepositoryImpl
    @Inject
    constructor(
        private val api: HouseholdApi,
    ) : HouseholdRepository {
        private var cache: List<HouseholdDto>? = null

        override fun getCached(): List<HouseholdDto>? = cache

        override suspend fun list(): List<HouseholdDto> = api.list().data.also { cache = it }

        override suspend fun create(name: String): HouseholdDto = api.create(CreateHouseholdRequest(name)).data

        override suspend fun join(code: String): HouseholdDto = api.join(JoinHouseholdRequest(code)).data

        override suspend fun update(
            householdId: Long,
            name: String?,
            color: String?,
            icon: String?,
        ): HouseholdDto {
            val body = UpdateHouseholdRequest(name = name, color = color, icon = icon)
            val updated = api.update(householdId, body).data
            cache = cache?.map { if (it.id == updated.id) updated else it }
            return updated
        }

        override suspend fun leave(householdId: Long) {
            api.leave(householdId)
            cache = cache?.filter { it.id != householdId }
        }

        override suspend fun delete(
            householdId: Long,
            nameConfirmation: String,
        ) {
            api.delete(householdId, DeleteHouseholdRequest(name = nameConfirmation))
            cache = cache?.filter { it.id != householdId }
        }

        override fun clear() {
            cache = null
        }
    }
