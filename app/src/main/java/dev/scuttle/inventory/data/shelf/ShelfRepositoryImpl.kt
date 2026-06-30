package dev.scuttle.inventory.data.shelf

import dev.scuttle.inventory.data.api.ShelfApi
import dev.scuttle.inventory.data.dto.CreateShelfRequest
import dev.scuttle.inventory.data.dto.ShelfDto
import javax.inject.Inject

class ShelfRepositoryImpl @Inject constructor(
    private val api: ShelfApi,
) : ShelfRepository {

    private val cache = mutableMapOf<Pair<Long, Long>, List<ShelfDto>>()

    override fun getCached(householdId: Long, locationId: Long): List<ShelfDto>? =
        cache[householdId to locationId]

    override suspend fun list(householdId: Long, locationId: Long): List<ShelfDto> =
        api.list(householdId, locationId).data.also { cache[householdId to locationId] = it }

    override suspend fun create(householdId: Long, locationId: Long, name: String): ShelfDto =
        api.create(householdId, locationId, CreateShelfRequest(name = name)).data.also { created ->
            val key = householdId to locationId
            cache[key] = (cache[key] ?: emptyList()) + created
        }

    override suspend fun delete(householdId: Long, locationId: Long, shelfId: Long) {
        api.delete(householdId, locationId, shelfId)
        val key = householdId to locationId
        cache[key] = cache[key]?.filter { it.id != shelfId } ?: emptyList()
    }
}
