package dev.scuttle.inventory.data.shelf

import dev.scuttle.inventory.data.api.ShelfApi
import dev.scuttle.inventory.data.dto.CreateShelfRequest
import dev.scuttle.inventory.data.dto.DeleteShelfRequest
import dev.scuttle.inventory.data.dto.ReorderRequest
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.dto.UpdateShelfRequest
import dev.scuttle.inventory.data.hierarchy.ShelfDeletion
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class ShelfRepositoryImpl
    @Inject
    constructor(
        private val api: ShelfApi,
    ) : ShelfRepository {
        // ConcurrentHashMap, not mutableMapOf: this singleton is written from ViewModel
        // coroutines (Main) and HierarchyStore's IO refresh at the same time; a plain
        // HashMap corrupts under that interleaving (BUG-3).
        private val cache = ConcurrentHashMap<Pair<Long, Long>, List<ShelfDto>>()

        override fun getCached(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto>? = cache[householdId to locationId]

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto> = api.list(householdId, locationId).data.also { cache[householdId to locationId] = it }

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ): ShelfDto =
            api.create(householdId, locationId, CreateShelfRequest(name = name)).data.also { created ->
                val key = householdId to locationId
                cache[key] = (cache[key] ?: emptyList()) + created
            }

        override suspend fun rename(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
            name: String,
        ): ShelfDto =
            api.update(householdId, locationId, shelfId, UpdateShelfRequest(name = name)).data.also { updated ->
                val key = householdId to locationId
                // On a cache miss, leave the cache absent rather than fabricating a
                // 1-element list — getCached() returning null means "go fetch", and a
                // bogus single-shelf cache would lie about every other shelf in this
                // location until the next full refresh.
                cache[key]?.let { cached ->
                    cache[key] = cached.map { if (it.id == shelfId) updated else it }
                }
            }

        override suspend fun reorder(
            householdId: Long,
            locationId: Long,
            ids: List<Long>,
        ): List<ShelfDto> =
            api.reorder(householdId, locationId, ReorderRequest(ids)).data.also {
                cache[householdId to locationId] = it
            }

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
            deletion: ShelfDeletion,
        ) {
            api.delete(
                householdId,
                locationId,
                shelfId,
                DeleteShelfRequest(
                    strategy = deletion.strategy?.wire,
                    target_shelf_id = deletion.targetShelfId,
                    deletion_batch_id = deletion.batchId,
                ),
            )
            val key = householdId to locationId
            cache[key] = cache[key]?.filter { it.id != shelfId } ?: emptyList()
        }

        override fun clear() {
            cache.clear()
        }
    }
