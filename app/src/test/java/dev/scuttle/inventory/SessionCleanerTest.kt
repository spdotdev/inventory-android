package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.auth.SessionCleaner
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.settings.DefaultHouseholdStore
import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.data.settings.HouseholdViewStore
import dev.scuttle.inventory.data.settings.ShelfViewStore
import dev.scuttle.inventory.data.shelf.ShelfRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCleanerTest {
    private class RecordingHouseholdRepo : HouseholdRepository {
        var cleared = false

        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list() = emptyList<HouseholdDto>()

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) {}

        override fun clear() {
            cleared = true
        }
    }

    private class RecordingLocationRepo : LocationRepository {
        var cleared = false

        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long) = emptyList<LocationDto>()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ) = throw NotImplementedError()

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
        ) {}

        override fun clear() {
            cleared = true
        }
    }

    private class RecordingShelfRepo : ShelfRepository {
        var cleared = false

        override fun getCached(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto>? = null

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ) = emptyList<ShelfDto>()

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ) = throw NotImplementedError()

        override suspend fun delete(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
        ) {}

        override fun clear() {
            cleared = true
        }
    }

    private class RecordingProductRepo : ProductRepository {
        var cleared = false

        override fun getCached(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto>? = null

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ) = emptyList<ProductDto>()

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            name: String,
            quantity: Int,
            code: String?,
        ) = throw NotImplementedError()

        override suspend fun update(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            edit: ProductEdit,
        ) = throw NotImplementedError()

        override suspend fun add(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = throw NotImplementedError()

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = throw NotImplementedError()

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            targetShelfId: Long,
        ) = throw NotImplementedError()

        override suspend fun uploadImage(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            imageUri: Uri,
            mimeType: String,
        ) = throw NotImplementedError()

        override suspend fun delete(
            householdId: Long,
            shelfId: Long,
            productId: Long,
        ) {}

        override fun clear() {
            cleared = true
        }
    }

    private class RecordingFavoritesStore : FavoritesStore {
        var cleared = false

        override fun getFavoriteLocations() = emptySet<Long>()

        override fun toggleFavoriteLocation(id: Long) {}

        override fun isFavoriteLocation(id: Long) = false

        override fun getFavoriteShelves() = emptySet<Long>()

        override fun toggleFavoriteShelf(id: Long) {}

        override fun isFavoriteShelf(id: Long) = false

        override fun clear() {
            cleared = true
        }
    }

    private class RecordingDefaultHouseholdStore : DefaultHouseholdStore {
        var cleared = false

        override fun get(): Long? = null

        override fun set(householdId: Long) {}

        override fun clear() {
            cleared = true
        }
    }

    private class RecordingShelfViewStore : ShelfViewStore {
        var cleared = false

        override fun isListView() = false

        override fun setListView(listView: Boolean) = Unit

        override fun clear() {
            cleared = true
        }
    }

    private class RecordingHouseholdViewStore : HouseholdViewStore {
        var cleared = false

        override fun collapsed() = emptySet<Long>()

        override fun toggleCollapsed(id: Long) = Unit

        override fun order() = emptyList<Long>()

        override fun setOrder(ids: List<Long>) = Unit

        override fun clear() {
            cleared = true
        }
    }

    @Test
    fun clear_fans_out_to_every_cache_and_store() {
        // X1: session end must wipe ALL per-account singleton state — miss one and
        // that slice of the previous user's data survives into the next login.
        val household = RecordingHouseholdRepo()
        val location = RecordingLocationRepo()
        val shelf = RecordingShelfRepo()
        val product = RecordingProductRepo()
        val favorites = RecordingFavoritesStore()
        val defaultHousehold = RecordingDefaultHouseholdStore()
        val shelfView = RecordingShelfViewStore()
        val householdView = RecordingHouseholdViewStore()
        val store = HierarchyStore(household, location, shelf, product)

        SessionCleaner(
            household,
            location,
            shelf,
            product,
            store,
            favorites,
            defaultHousehold,
            shelfView,
            householdView,
        ).clear()

        assertTrue(household.cleared)
        assertTrue(location.cleared)
        assertTrue(shelf.cleared)
        assertTrue(product.cleared)
        assertTrue(favorites.cleared)
        assertTrue(defaultHousehold.cleared)
        assertTrue(shelfView.cleared)
        assertTrue(householdView.cleared)
    }
}
