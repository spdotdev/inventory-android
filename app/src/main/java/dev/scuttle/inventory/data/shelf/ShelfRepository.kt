package dev.scuttle.inventory.data.shelf

import dev.scuttle.inventory.data.dto.ShelfDto

interface ShelfRepository {
    fun getCached(householdId: Long, locationId: Long): List<ShelfDto>?

    suspend fun list(householdId: Long, locationId: Long): List<ShelfDto>

    suspend fun create(householdId: Long, locationId: Long, name: String): ShelfDto

    suspend fun delete(householdId: Long, locationId: Long, shelfId: Long)
}
