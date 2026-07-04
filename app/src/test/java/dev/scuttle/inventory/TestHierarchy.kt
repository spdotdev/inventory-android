package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository

/**
 * Builds a real [HierarchyStore] over empty location/shelf/product repos for VM
 * tests that only need to verify a store refresh was triggered (X4) — the store's
 * entries then reflect whatever the supplied [HouseholdRepository] returns.
 */
object TestHierarchy {
    private object EmptyLocations : LocationRepository {
        override fun getCached(householdId: Long): List<LocationDto>? = null
        override suspend fun list(householdId: Long) = emptyList<LocationDto>()
        override suspend fun create(householdId: Long, name: String, type: String) = throw NotImplementedError()
        override suspend fun delete(householdId: Long, locationId: Long) {}
    }

    private object EmptyShelves : ShelfRepository {
        override fun getCached(householdId: Long, locationId: Long): List<ShelfDto>? = null
        override suspend fun list(householdId: Long, locationId: Long) = emptyList<ShelfDto>()
        override suspend fun create(householdId: Long, locationId: Long, name: String) = throw NotImplementedError()
        override suspend fun delete(householdId: Long, locationId: Long, shelfId: Long) {}
    }

    private object EmptyProducts : ProductRepository {
        override fun getCached(householdId: Long, shelfId: Long): List<ProductDto>? = null
        override suspend fun list(householdId: Long, shelfId: Long) = emptyList<ProductDto>()
        override suspend fun create(householdId: Long, shelfId: Long, name: String, quantity: Int) = throw NotImplementedError()
        override suspend fun update(householdId: Long, shelfId: Long, productId: Long, name: String, description: String?, code: String?, isMandatory: Boolean) = throw NotImplementedError()
        override suspend fun add(householdId: Long, shelfId: Long, productId: Long, amount: Int) = throw NotImplementedError()
        override suspend fun remove(householdId: Long, shelfId: Long, productId: Long, amount: Int) = throw NotImplementedError()
        override suspend fun move(householdId: Long, shelfId: Long, productId: Long, targetShelfId: Long) = throw NotImplementedError()
        override suspend fun uploadImage(householdId: Long, shelfId: Long, productId: Long, imageUri: Uri, mimeType: String) = throw NotImplementedError()
        override suspend fun delete(householdId: Long, shelfId: Long, productId: Long) {}
    }

    fun store(householdRepository: HouseholdRepository): HierarchyStore =
        HierarchyStore(householdRepository, EmptyLocations, EmptyShelves, EmptyProducts)
}
