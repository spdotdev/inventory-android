package dev.scuttle.inventory.data.lowstock

interface LowStockRepository {
    suspend fun count(): Int?
}
