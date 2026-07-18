package dev.scuttle.inventory.data.location

import dev.scuttle.inventory.data.api.LocationApi
import dev.scuttle.inventory.data.dto.CreateLocationRequest
import dev.scuttle.inventory.data.dto.DeleteLocationRequest
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ReorderRequest
import dev.scuttle.inventory.data.dto.UpdateLocationRequest
import dev.scuttle.inventory.data.hierarchy.LocationDeletion
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class LocationRepositoryImpl
    @Inject
    constructor(
        private val api: LocationApi,
    ) : LocationRepository {
        // ConcurrentHashMap, not mutableMapOf: this singleton is written from ViewModel
        // coroutines (Main) and HierarchyStore's IO refresh at the same time; a plain
        // HashMap corrupts under that interleaving (BUG-3).
        private val cache = ConcurrentHashMap<Long, List<LocationDto>>()

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

        override suspend fun rename(
            householdId: Long,
            locationId: Long,
            name: String,
            type: String,
        ): LocationDto =
            api
                .update(householdId, locationId, UpdateLocationRequest(name = name, type = type))
                .data
                .also { updated ->
                    // On a cache miss, leave the cache absent rather than fabricating a
                    // 1-element list — getCached() returning null means "go fetch", and a
                    // bogus single-location cache would lie about every other location in
                    // this household until the next full refresh.
                    cache[householdId]?.let { cached ->
                        cache[householdId] = cached.map { if (it.id == locationId) updated else it }
                    }
                }

        override suspend fun reorder(
            householdId: Long,
            ids: List<Long>,
        ): List<LocationDto> =
            api.reorder(householdId, ReorderRequest(ids)).data.also {
                cache[householdId] = it
            }

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            deletion: LocationDeletion,
        ) {
            api.delete(
                householdId,
                locationId,
                DeleteLocationRequest(
                    strategy = deletion.strategy?.wire,
                    target_location_id = deletion.targetLocationId,
                    deletion_batch_id = deletion.batchId,
                ),
            )
            cache[householdId] = cache[householdId]?.filter { it.id != locationId } ?: emptyList()
        }

        override fun clear() {
            cache.clear()
        }
    }
