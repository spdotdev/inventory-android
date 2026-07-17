package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Builds a real [HierarchyStore] over empty location/shelf/product repos for VM
 * tests that only need to verify a store refresh was triggered (X4) — the store's
 * entries then reflect whatever the supplied [HouseholdRepository] returns.
 */
object TestHierarchy {
    private object EmptyLocations : LocationRepository {
        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long) = emptyList<LocationDto>()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ) = throw NotImplementedError()
    }

    private object EmptyShelves : ShelfRepository {
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
    }

    private object EmptyProducts : ProductRepository {
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
        ) = "batch"
    }

    /**
     * @param dispatcher Defaults to a FRESH, unconfined test dispatcher — never the
     *   production `Dispatchers.IO` the real HierarchyStore falls back to (Minor 5,
     *   Task 5/5b review). Tasks 5/5b made every confirm/undo/rename trigger a
     *   `hierarchyStore.refresh()` under test; a real IO-backed scope outside
     *   `runTest`'s control can still be mid-flight when a test finishes and
     *   `Dispatchers.Main` resets, surfacing as an intermittent failure in whatever
     *   OTHER test runs next. Unconfined runs `refresh()`'s coroutine eagerly on
     *   the calling thread instead, so nothing is left running once a test ends.
     */
    fun store(
        householdRepository: HouseholdRepository,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ): HierarchyStore = HierarchyStore(householdRepository, EmptyLocations, EmptyShelves, EmptyProducts, dispatcher)

    /**
     * A minimal, shared [AuthRepository] fake for VMs that only need to observe
     * [AuthRepository.sessionActive] (e.g. DrawerViewModel's session-reset —
     * DrawerViewModelTest / DrawerViewModelSessionResetTest). Starts `true`
     * (authenticated) since that's every existing test's starting condition;
     * [setActive] simulates a session boundary (logout, 401, or a fresh sign-in).
     * Every other member is unused by those VMs and throws if ever called.
     */
    class FakeAuthRepository : AuthRepository {
        private val active = MutableStateFlow(true)

        override fun isAuthenticated() = active.value

        override val sessionActive: StateFlow<Boolean> = active

        override suspend fun register(
            name: String,
            email: String,
            password: String,
        ) = throw NotImplementedError()

        override suspend fun login(
            email: String,
            password: String,
        ) = throw NotImplementedError()

        override suspend fun loginWithGoogle(idToken: String) = throw NotImplementedError()

        override suspend fun loginWithGoogleCode(
            code: String,
            codeVerifier: String,
            redirectUri: String,
        ) = throw NotImplementedError()

        override suspend fun forgotPassword(email: String) = throw NotImplementedError()

        override suspend fun logout() = throw NotImplementedError()

        fun setActive(value: Boolean) {
            active.value = value
        }
    }
}
