package dev.scuttle.inventory.data.missingitems

interface MissingItemsRepository {
    suspend fun count(): Int?
}
