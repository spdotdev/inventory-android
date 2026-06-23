package dev.scuttle.inventory.data.shelf

import dev.scuttle.inventory.data.api.ShelfApi
import dev.scuttle.inventory.data.dto.CreateShelfRequest
import dev.scuttle.inventory.data.dto.ShelfDto
import javax.inject.Inject

class ShelfRepositoryImpl @Inject constructor(
    private val api: ShelfApi,
) : ShelfRepository {

    override suspend fun list(householdId: Long, locationId: Long): List<ShelfDto> =
        api.list(householdId, locationId).data

    override suspend fun create(householdId: Long, locationId: Long, name: String): ShelfDto =
        api.create(householdId, locationId, CreateShelfRequest(name = name)).data
}
