package dev.scuttle.inventory

import dev.scuttle.inventory.data.api.LocationApi
import dev.scuttle.inventory.data.dto.CreateLocationRequest
import dev.scuttle.inventory.data.dto.DeleteLocationRequest
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.LocationListResponse
import dev.scuttle.inventory.data.dto.LocationResponse
import dev.scuttle.inventory.data.dto.ReorderRequest
import dev.scuttle.inventory.data.dto.UpdateLocationRequest
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.location.LocationRepositoryImpl
import dev.scuttle.inventory.data.product.ProductRepositoryImpl
import dev.scuttle.inventory.data.shelf.ShelfRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentMap
import kotlin.concurrent.thread

/**
 * BUG-3 regression guard: the singleton repositories' caches are written from two
 * dispatchers at once — ViewModel coroutines resuming on Main, and
 * HierarchyStore.buildFromNetwork tallying on IO (live-update pings / pull-to-refresh).
 * A plain HashMap corrupts or throws under that interleaving, so the backing map of
 * every repository cache must be a ConcurrentMap.
 */
class RepositoryCacheConcurrencyTest {
    private class FakeLocationApi : LocationApi {
        override suspend fun list(householdId: Long) =
            LocationListResponse(listOf(LocationDto(id = householdId, name = "L$householdId", type = "pantry")))

        override suspend fun create(
            householdId: Long,
            body: CreateLocationRequest,
        ) = LocationResponse(LocationDto(id = 1, name = body.name, type = body.type))

        override suspend fun update(
            householdId: Long,
            locationId: Long,
            body: UpdateLocationRequest,
        ) = LocationResponse(LocationDto(id = locationId, name = body.name ?: "", type = body.type ?: "pantry"))

        override suspend fun reorder(
            householdId: Long,
            body: ReorderRequest,
        ) = LocationListResponse(emptyList())

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
            body: DeleteLocationRequest,
        ) = Unit
    }

    @Test
    fun `every repository cache is backed by a concurrent map`() {
        for (repoClass in listOf(
            LocationRepositoryImpl::class,
            ShelfRepositoryImpl::class,
            ProductRepositoryImpl::class,
        )) {
            val field = repoClass.java.getDeclaredField("cache").apply { isAccessible = true }
            assertTrue(
                "${repoClass.simpleName}.cache must be a ConcurrentMap (written from Main and IO at once)",
                ConcurrentMap::class.java.isAssignableFrom(field.type) ||
                    // the field's declared type may be Map; check the runtime instance instead
                    field.get(newInstanceOf(repoClass.java)) is ConcurrentMap<*, *>,
            )
        }
    }

    /** Stress the real repository from two threads — corrupts/throws on a plain HashMap. */
    @Test
    fun `concurrent list calls do not corrupt the cache`() {
        val repo: LocationRepository = LocationRepositoryImpl(FakeLocationApi())
        val errors = mutableListOf<Throwable>()
        val threads =
            (0 until 4).map { t ->
                thread {
                    try {
                        runBlocking {
                            repeat(2_000) { i -> repo.list((t * 10_000 + i).toLong()) }
                        }
                    } catch (e: Throwable) {
                        synchronized(errors) { errors += e }
                    }
                }
            }
        threads.forEach { it.join() }
        assertTrue("concurrent cache writes threw: ${errors.firstOrNull()}", errors.isEmpty())
        repeat(4) { t -> checkNotNull(repo.getCached((t * 10_000).toLong())) { "entry lost for thread $t" } }
    }

    private fun newInstanceOf(clazz: Class<*>): Any {
        val ctor = clazz.declaredConstructors.first().apply { isAccessible = true }
        val args = ctor.parameterTypes.map { if (it == LocationApi::class.java) FakeLocationApi() else null }
        return ctor.newInstance(*args.toTypedArray())
    }
}
