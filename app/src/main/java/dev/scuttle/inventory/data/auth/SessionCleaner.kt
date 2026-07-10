package dev.scuttle.inventory.data.auth

import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.settings.DefaultHouseholdStore
import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.data.shelf.ShelfRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes every per-account in-memory cache + local pref so one user's data never
 * bleeds into the next session. The repositories, [HierarchyStore], and the pref
 * stores are all `@Singleton`, so without this an account switch on the same
 * process would render the previous user's households/locations/products/
 * missing-items (and favorites) to the new user — `HierarchyStore.refresh()`
 * calls `loadFromCache()`, which repopulates synchronously from the stale caches
 * before the network returns. In-memory/prefs only, so it respects the
 * always-online, no-local-DB rule.
 */
@Singleton
class SessionCleaner
    @Inject
    constructor(
        private val householdRepository: HouseholdRepository,
        private val locationRepository: LocationRepository,
        private val shelfRepository: ShelfRepository,
        private val productRepository: ProductRepository,
        private val hierarchyStore: HierarchyStore,
        private val favoritesStore: FavoritesStore,
        private val defaultHouseholdStore: DefaultHouseholdStore,
    ) {
        fun clear() {
            householdRepository.clear()
            locationRepository.clear()
            shelfRepository.clear()
            productRepository.clear()
            hierarchyStore.clear()
            favoritesStore.clear()
            defaultHouseholdStore.clear()
        }
    }
