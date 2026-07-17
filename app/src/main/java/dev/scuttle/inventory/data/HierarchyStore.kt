package dev.scuttle.inventory.data

import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.error.toUserMessage
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class HouseholdWithLocations(
    val id: Long,
    val name: String,
    val locations: List<LocationDto>,
    // Theme keys (null = derived default) — carried so the drawer avatar
    // reflects a user-chosen theme without a second repository lookup.
    val color: String? = null,
    val icon: String? = null,
    // Mirrors HouseholdDto.can_restructure so screens that render every
    // household at once (AllStoragesScreen) can gate a per-row
    // restructure-capable affordance (e.g. delete) without a second lookup.
    // Defaults true so a caller that doesn't know the household's role yet
    // never hides something the server would actually allow.
    val canRestructure: Boolean = true,
)

data class LocationStats(
    val location: LocationDto,
    val householdId: Long,
    val productCount: Int,
)

data class ShelfEntry(
    val householdId: Long,
    val shelf: ShelfDto,
)

data class MissingItem(
    val productName: String,
    val shelfName: String,
    val locationName: String,
    val householdId: Long,
    val locationId: Long,
)

data class LowStockItem(
    val productName: String,
    val quantity: Int,
    val threshold: Int,
    val shelfName: String,
    val locationName: String,
    val householdId: Long,
    val locationId: Long,
)

data class HierarchyState(
    val loading: Boolean = false,
    // Distinct from `loading`: true only during a user-initiated pull-to-refresh /
    // refresh-button reload, so the pull indicator doesn't spin on a post-mutation
    // silent reload. Drives PullToRefreshBox.isRefreshing (D-008).
    val refreshing: Boolean = false,
    val error: String? = null,
    val entries: List<HouseholdWithLocations> = emptyList(),
    val missingItems: List<MissingItem> = emptyList(),
    val missingItemCount: Int = 0,
    // Phase 2 "running low": quantity <= low_stock_threshold, threshold set. Items
    // already counted missing (mandatory + qty 0) are excluded — one warning per item.
    val lowStockItems: List<LowStockItem> = emptyList(),
    val totalShelves: Int = 0,
    val totalProducts: Int = 0,
    val mandatoryWarnings: Int = 0,
    val locationStats: List<LocationStats> = emptyList(),
    val locationWarnings: Map<Long, Boolean> = emptyMap(),
    val allShelves: List<ShelfEntry> = emptyList(),
)

/**
 * Mirrors [dev.scuttle.inventory.data.realtime.LiveUpdates]'s own constructor
 * shape: the primary constructor below takes the [dispatcher] its internal
 * `scope` runs on, and the Hilt-visible constructor delegates to it with the
 * real [Dispatchers.IO]. Before this, `scope` was hard-coded to
 * `CoroutineScope(SupervisorJob() + Dispatchers.IO)` — a REAL background
 * thread outside any test's control. A test that triggered a refresh (every
 * confirm/undo/rename does, as of Task 5/5b) could finish and reset
 * `Dispatchers.Main` while that thread was still mid-flight; its later
 * `_state.update`/`_state.value =` write then woke a still-active
 * `stateIn(viewModelScope)` collector after Main was gone, surfacing as an
 * intermittent "Main dispatcher not set" failure in whatever OTHER test
 * happened to run next. Tests should reach the primary constructor with a
 * test dispatcher instead — see TestHierarchy.store().
 */
@Singleton
class HierarchyStore(
    private val householdRepository: HouseholdRepository,
    private val locationRepository: LocationRepository,
    private val shelfRepository: ShelfRepository,
    private val productRepository: ProductRepository,
    dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        householdRepository: HouseholdRepository,
        locationRepository: LocationRepository,
        shelfRepository: ShelfRepository,
        productRepository: ProductRepository,
    ) : this(householdRepository, locationRepository, shelfRepository, productRepository, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var activeJob: Job? = null

    private val _state = MutableStateFlow(HierarchyState())
    val state: StateFlow<HierarchyState> = _state.asStateFlow()

    fun loadFromCache() {
        val households = householdRepository.getCached() ?: return
        var totalShelves = 0
        var totalProducts = 0
        var mandatoryWarnings = 0
        val entries = mutableListOf<HouseholdWithLocations>()
        val locationStats = mutableListOf<LocationStats>()
        val missingItems = mutableListOf<MissingItem>()
        val lowStockItems = mutableListOf<LowStockItem>()
        val allShelves = mutableListOf<ShelfEntry>()
        for (hh in households) {
            val locations = locationRepository.getCached(hh.id) ?: emptyList()
            for (location in locations) {
                var count = 0
                val shelves = shelfRepository.getCached(hh.id, location.id) ?: emptyList()
                totalShelves += shelves.size
                for (shelf in shelves) {
                    allShelves += ShelfEntry(hh.id, shelf)
                    val products = productRepository.getCached(hh.id, shelf.id) ?: emptyList()
                    totalProducts += products.size
                    count += products.size
                    val place = ProductPlace(shelf, location, hh.id)
                    for (p in products) {
                        mandatoryWarnings += accumulate(classify(p, place), missingItems, lowStockItems)
                    }
                }
                locationStats += LocationStats(location, hh.id, count)
            }
            entries += HouseholdWithLocations(hh.id, hh.name, locations, hh.color, hh.icon, hh.can_restructure)
        }
        missingItems.sortWith(compareBy({ it.locationName }, { it.shelfName }, { it.productName }))
        lowStockItems.sortWith(compareBy({ it.locationName }, { it.shelfName }, { it.productName }))
        _state.update { s ->
            s.copy(
                entries = entries,
                missingItems = missingItems,
                missingItemCount = mandatoryWarnings,
                lowStockItems = lowStockItems,
                totalShelves = totalShelves,
                totalProducts = totalProducts,
                mandatoryWarnings = mandatoryWarnings,
                locationStats = locationStats,
                allShelves = allShelves,
                locationWarnings = warningsByLocation(missingItems),
            )
        }
    }

    /**
     * Which locations have at least one missing mandatory item, derived from the
     * full product set (X2). The home/drawer "⚠ needs attention" indicator reads
     * this — before, it was only ever populated by a composed ProductsPane, so a
     * missing item on a non-visible shelf showed no warning until the user opened
     * that location and swiped to the shelf.
     */
    private fun warningsByLocation(missingItems: List<MissingItem>): Map<Long, Boolean> =
        missingItems.map { it.locationId }.associateWith { true }

    /** Where a product lives — shared context for building warning items. */
    private data class ProductPlace(
        val shelf: ShelfDto,
        val location: LocationDto,
        val householdId: Long,
    )

    private sealed interface Warning {
        data class Missing(
            val item: MissingItem,
        ) : Warning

        data class Low(
            val item: LowStockItem,
        ) : Warning
    }

    /**
     * Classify one product into the warning bucket shared by the cache and network
     * paths: mandatory-at-zero -> missing; else at/below its low-stock threshold ->
     * running low. Mutually exclusive so an item never carries two warnings.
     */
    private fun classify(
        product: dev.scuttle.inventory.data.dto.ProductDto,
        place: ProductPlace,
    ): Warning? {
        val threshold = product.low_stock_threshold
        return when {
            product.is_mandatory == true && product.quantity == 0 ->
                Warning.Missing(
                    MissingItem(
                        product.name,
                        place.shelf.name,
                        place.location.name,
                        place.householdId,
                        place.location.id,
                    ),
                )
            threshold != null && product.quantity <= threshold ->
                Warning.Low(
                    LowStockItem(
                        productName = product.name,
                        quantity = product.quantity,
                        threshold = threshold,
                        shelfName = place.shelf.name,
                        locationName = place.location.name,
                        householdId = place.householdId,
                        locationId = place.location.id,
                    ),
                )
            else -> null
        }
    }

    /** Apply one product's warning to the accumulators; returns the missing increment. */
    private fun accumulate(
        warning: Warning?,
        missing: MutableList<MissingItem>,
        lowStock: MutableList<LowStockItem>,
    ): Int =
        when (warning) {
            is Warning.Missing -> {
                missing += warning.item
                1
            }
            is Warning.Low -> {
                lowStock += warning.item
                0
            }
            null -> 0
        }

    /**
     * @param userInitiated true when the user pulled to refresh or tapped refresh —
     *   flips `refreshing` so the pull indicator spins. A post-mutation reload passes
     *   false: the list still reloads (`loading`), but the pull indicator stays put.
     */
    fun refresh(userInitiated: Boolean = false) {
        activeJob?.cancel()
        _state.update { it.copy(loading = true, refreshing = userInitiated, error = null) }
        loadFromCache()
        activeJob =
            scope.launch {
                runCatching {
                    val households = householdRepository.list()
                    buildFromNetwork(households)
                }.fold(
                    // buildFromNetwork returns a fresh state with loading/refreshing at their
                    // false defaults, so both indicators clear on success. Its locationWarnings
                    // are now computed authoritatively from the full server product set (X2),
                    // so we take them as-is rather than preserving the old partial map that only
                    // the visible ProductsPane could populate — the API is server-authoritative,
                    // and a mutation refreshes after the server confirms, so this is the truth.
                    onSuccess = { update -> _state.value = update },
                    onFailure = { e ->
                        _state.update {
                            it.copy(loading = false, refreshing = false, error = e.toUserMessage("Failed to load."))
                        }
                    },
                )
            }
    }

    /**
     * Reset to the empty state and drop any in-flight load. Called on session end
     * (logout / new login) so one account's hierarchy never renders to the next —
     * loadFromCache() would otherwise repopulate it from the singleton repo caches.
     */
    fun clear() {
        activeJob?.cancel()
        _state.value = HierarchyState()
    }

    fun reportLocationWarning(
        locationId: Long,
        hasWarning: Boolean,
    ) {
        _state.update { state ->
            state.copy(locationWarnings = state.locationWarnings + (locationId to hasWarning))
        }
    }

    /** Fetch one shelf's products and classify them; returns (productCount, missingCount). */
    private suspend fun tallyShelf(
        householdId: Long,
        location: LocationDto,
        shelf: ShelfDto,
        missing: MutableList<MissingItem>,
        lowStock: MutableList<LowStockItem>,
    ): Pair<Int, Int> {
        val products = productRepository.list(householdId, shelf.id)
        val place = ProductPlace(shelf, location, householdId)
        var missingCount = 0
        for (product in products) {
            missingCount += accumulate(classify(product, place), missing, lowStock)
        }
        return products.size to missingCount
    }

    private suspend fun buildFromNetwork(
        households: List<dev.scuttle.inventory.data.dto.HouseholdDto>,
    ): HierarchyState {
        val loading = false
        val error: String? = null
        var totalShelves = 0
        var totalProducts = 0
        var mandatoryWarnings = 0
        val entries = mutableListOf<HouseholdWithLocations>()
        val locationStats = mutableListOf<LocationStats>()
        val missingItems = mutableListOf<MissingItem>()
        val lowStockItems = mutableListOf<LowStockItem>()
        val allShelves = mutableListOf<ShelfEntry>()

        for (hh in households) {
            val locations = locationRepository.list(hh.id)
            for (location in locations) {
                var locationProductCount = 0
                val shelves = shelfRepository.list(hh.id, location.id)
                totalShelves += shelves.size
                for (shelf in shelves) {
                    allShelves += ShelfEntry(hh.id, shelf)
                    val (productCount, missingCount) =
                        tallyShelf(hh.id, location, shelf, missingItems, lowStockItems)
                    totalProducts += productCount
                    locationProductCount += productCount
                    mandatoryWarnings += missingCount
                }
                locationStats += LocationStats(location, hh.id, locationProductCount)
            }
            entries += HouseholdWithLocations(hh.id, hh.name, locations, hh.color, hh.icon, hh.can_restructure)
        }

        missingItems.sortWith(compareBy({ it.locationName }, { it.shelfName }, { it.productName }))
        lowStockItems.sortWith(compareBy({ it.locationName }, { it.shelfName }, { it.productName }))

        return HierarchyState(
            loading = loading,
            error = error,
            entries = entries,
            missingItems = missingItems,
            missingItemCount = mandatoryWarnings,
            lowStockItems = lowStockItems,
            totalShelves = totalShelves,
            totalProducts = totalProducts,
            mandatoryWarnings = mandatoryWarnings,
            locationStats = locationStats,
            allShelves = allShelves,
            locationWarnings = warningsByLocation(missingItems),
        )
    }
}
