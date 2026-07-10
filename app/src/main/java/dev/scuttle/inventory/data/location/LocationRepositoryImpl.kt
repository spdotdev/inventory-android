package dev.scuttle.inventory.data.location

import dev.scuttle.inventory.data.api.LocationApi
import dev.scuttle.inventory.data.dto.CreateLocationRequest
import dev.scuttle.inventory.data.dto.LocationDto
import javax.inject.Inject

class LocationRepositoryImpl
    @Inject
    constructor(
        private val api: LocationApi,
    ) : LocationRepository {
        private val cache = mutableMapOf<Long, List<LocationDto>>()

        override fun getCached(householdId: Long): List<LocationDto>? = cache[householdId]

        override suspend fun list(householdId: Long): List<LocationDto> =
            api.list(householdId).data.also { cache[householdId] = it }

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ): LocationDto =
            api.create(householdId, CreateLocationRequest(name = name, type = type)).data.also { created ->
                cache[householdId] = (cache[householdId] ?: emptyList()) + created
            }

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
        ) {
            api.delete(householdId, locationId)
            cache[householdId] = cache[householdId]?.filter { it.id != locationId } ?: emptyList()
        }

        override fun clear() {
            cache.clear()
        }
    }
