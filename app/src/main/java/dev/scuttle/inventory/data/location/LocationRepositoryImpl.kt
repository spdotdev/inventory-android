package dev.scuttle.inventory.data.location

import dev.scuttle.inventory.data.api.LocationApi
import dev.scuttle.inventory.data.dto.CreateLocationRequest
import dev.scuttle.inventory.data.dto.LocationDto
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val api: LocationApi,
) : LocationRepository {

    override suspend fun list(householdId: Long): List<LocationDto> = api.list(householdId).data

    override suspend fun create(householdId: Long, name: String, type: String): LocationDto =
        api.create(householdId, CreateLocationRequest(name = name, type = type)).data

    override suspend fun delete(householdId: Long, locationId: Long) =
        api.delete(householdId, locationId)
}
