# Storage Architecture Editing — Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user rename, reorder, and safely delete the storage hierarchy from the screens that already show it — and replace today's no-confirmation bulk shelf delete with a flow that asks what to do with the contents.

**Architecture:** One **edit mode**, toggled by a pencil in the top bar, applied identically to the households / locations / shelves lists. In it, each row grows a checkbox (multi-select delete) and move-up/move-down buttons (reorder), and tapping the row body opens its edit sheet. Deleting a non-empty container opens a strategy dialog; the client mints one `deletion_batch_id` per gesture so a single snackbar Undo restores the whole thing.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit + **kotlinx.serialization**, MVVM with `StateFlow<XUiState>`. JUnit4 + hand-written fakes (no MockK/Mockito). Instrumented flow tests on MockWebServer + Hilt.

**Depends on:** the backend plan (`inventory-laravel/docs/superpowers/plans/2026-07-13-storage-architecture-editing-backend.md`) must ship first — every endpoint here is new.

## Global Constraints

Copied verbatim from the codebase's conventions. Every task's requirements implicitly include this section.

- **No `Result`, no sealed error types.** Repositories **throw**; ViewModels catch with `runCatching` and map via `Throwable.toUserMessage(fallback)` (`data/error/ErrorMapping.kt`). Those fallback strings are **hardcoded English in Kotlin**, not `strings.xml` — match that.
- **A new repository-interface method MUST have a default implementation** (`= throw UnsupportedOperationException("x not supported")`, as `HouseholdRepository.updateTheme` does). Otherwise ~6 hand-written test fakes fail to compile.
- **A new Api interface must be registered in BOTH `di/NetworkModule.kt` AND `androidTest/.../di/TestNetworkModule.kt`** (`@TestInstallIn(replaces = [NetworkModule::class])`), or every flow test fails to inject.
- **A new PATCH/update request DTO must NOT give its properties defaults.** The app's `Json` has `encodeDefaults = false`, so a defaulted field is silently **omitted from the body** and the server keeps the old value. See the comment on `UpdateHouseholdThemeRequest` and `UpdateProductRequestSerializationTest`.
- **Mutations use `launchLoading { }`** (default `refreshing = false`). Only a user-initiated pull uses `launchLoading(refreshing = true)`. There is a test asserting this (`StorageOverviewViewModelTest.refresh_flags_the_pull_spinner_but_create_does_not`).
- **Any mutation that changes the hierarchy must call `hierarchyStore.refresh()`**, or the Dashboard, AllStorages and the missing-items badge go stale.
- **A new per-account store must be wired into `data/auth/SessionCleaner.clear()`**, or one account's state bleeds into the next session.
- **Preferences are `SharedPreferences`, not DataStore.** DataStore is not a dependency of this project. Follow `interface XStore` in `data/settings/` + `class SharedPrefsXStore(context: Context) : XStore` (plain constructor, no `@Inject`) + `@Provides` in `di/StorageModule.kt`.
- **Every new user-facing string goes in BOTH `res/values/strings.xml` and `res/values-nl/strings.xml`.** Lint is `abortOnError = false`, so nothing catches a missing translation — it is discipline only. Keys are `snake_case`, prefixed by screen; `_cd` suffix = content description.
- **`ktlintCheck` and `detekt` are blocking CI gates** and are baseline-aware, so **new code must be clean**. Reproduce the existing style exactly, including the unusual `@Inject constructor` indentation and trailing commas everywhere. Non-`@Composable` functions still face `LongMethod` / `LongParameterList` / `MagicNumber`.
- **Run tests with `./gradlew testDebugUnitTest`; style with `./gradlew ktlintCheck detekt`.** Instrumented: `./gradlew connectedDebugAndroidTest` (nightly in CI, not on PR).

## Deviation from the spec — read before starting

The spec says **drag handles** for reorder. This plan uses **move-up / move-down icon buttons** instead:

- Compose has no built-in reorderable list. Drag means a new third-party dependency, or ~150 lines of gesture code that will fight the existing `SwipeToDismissBox` on these very rows.
- These lists are 3–8 items. Buttons are precise where drag is fiddly.
- This codebase takes accessibility seriously (`liveRegion = Assertive`, `_cd` strings everywhere). **Drag is the worst possible reorder affordance for TalkBack;** discrete buttons are the best.

The ViewModel contract (`moveUp(id)` / `moveDown(id)` producing a full ordered id list) is identical either way, so swapping in drag later touches only the row composable.

---

## Task 1: Data layer — rename, reorder, delete-with-strategy

**Files:**
- Modify: `data/api/LocationApi.kt`, `data/api/ShelfApi.kt`, `data/api/HouseholdApi.kt`, `data/dto/LocationDtos.kt`, `data/dto/ShelfDtos.kt`, `data/dto/HouseholdDtos.kt`, `data/location/LocationRepository.kt`, `data/location/LocationRepositoryImpl.kt`, `data/shelf/ShelfRepository.kt`, `data/shelf/ShelfRepositoryImpl.kt`, `data/household/HouseholdRepository.kt`, `data/household/HouseholdRepositoryImpl.kt`
- Create: `data/api/RestoreApi.kt`, `data/dto/RestoreDtos.kt`, `data/hierarchy/DeleteStrategy.kt`
- Modify: `di/NetworkModule.kt`, `app/src/androidTest/java/dev/scuttle/inventory/di/TestNetworkModule.kt`
- Test: `app/src/test/java/dev/scuttle/inventory/DeleteRequestSerializationTest.kt`

**Interfaces produced (later tasks depend on these exact signatures):**

```kotlin
// data/hierarchy/DeleteStrategy.kt
enum class ShelfDeleteStrategy(val wire: String) {
    MOVE_PRODUCTS("move_products"),
    UNSORT_PRODUCTS("unsort_products"),
    DELETE_PRODUCTS("delete_products"),
}
enum class LocationDeleteStrategy(val wire: String) {
    MOVE_CONTENTS("move_contents"),
    DELETE_CONTENTS("delete_contents"),
}

// LocationRepository
suspend fun rename(householdId: Long, locationId: Long, name: String, type: String): LocationDto
suspend fun reorder(householdId: Long, ids: List<Long>): List<LocationDto>
suspend fun delete(householdId: Long, locationId: Long, batchId: String, strategy: LocationDeleteStrategy?, targetLocationId: Long?)

// ShelfRepository
suspend fun rename(householdId: Long, locationId: Long, shelfId: Long, name: String): ShelfDto
suspend fun reorder(householdId: Long, locationId: Long, ids: List<Long>): List<ShelfDto>
suspend fun delete(householdId: Long, locationId: Long, shelfId: Long, batchId: String, strategy: ShelfDeleteStrategy?, targetShelfId: Long?)

// HouseholdRepository
suspend fun update(householdId: Long, name: String?, color: String?, icon: String?): HouseholdDto

// RestoreRepository (new)
suspend fun restore(householdId: Long, batchId: String): Int
```

> **Note on `ShelfRepository.delete`:** the existing signature `delete(householdId, locationId, shelfId)` gains three parameters. Give them defaults (`batchId`, `strategy = null`, `targetShelfId = null`) **only on the interface** so existing fakes keep compiling — but `batchId` is required by the server, so it has no default.

- [ ] **Step 1: Write the failing serialization test**

This is the trap that has bitten this codebase before. Create `app/src/test/java/dev/scuttle/inventory/DeleteRequestSerializationTest.kt`:

```kotlin
package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.DeleteShelfRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteRequestSerializationTest {
    // The app's real Json config. encodeDefaults defaults to false, which is
    // exactly why a defaulted property would be dropped from the body.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun batch_id_is_always_sent() {
        val body =
            json.encodeToString(
                DeleteShelfRequest(
                    strategy = null,
                    target_shelf_id = null,
                    deletion_batch_id = "11111111-1111-4111-8111-111111111111",
                ),
            )

        // The server 422s without it. If a default ever creeps onto this
        // property, encodeDefaults=false silently omits it and every delete
        // starts failing with a validation error nobody can explain.
        assertTrue(body.contains("deletion_batch_id"))
    }

    @Test
    fun a_null_strategy_is_still_sent_explicitly() {
        val body =
            json.encodeToString(
                DeleteShelfRequest(
                    strategy = null,
                    target_shelf_id = null,
                    deletion_batch_id = "11111111-1111-4111-8111-111111111111",
                ),
            )

        // An empty shelf legitimately has no strategy. Sending explicit null is
        // fine; what must never happen is the property vanishing because it was
        // defaulted.
        assertTrue(body.contains("\"strategy\":null"))
        assertFalse(body.isBlank())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DeleteRequestSerializationTest*'`
Expected: FAIL — `DeleteShelfRequest` does not exist.

- [ ] **Step 3: Add the DTOs**

In `data/dto/ShelfDtos.kt` — add `is_system` to `ShelfDto` and the new request bodies. **No defaults on the request properties.**

```kotlin
@Serializable
data class ShelfDto(
    val id: Long,
    val name: String,
    val position: Int? = null,
    val location_id: Long,
    // Server-side flag for the "Unsorted" holding shelf. The client localises the
    // label off this — the server always stores the literal name "Unsorted".
    val is_system: Boolean = false,
    // The server requires a delete STRATEGY when a shelf has products. This is how
    // the client knows to ask, and it feeds the dialog's "17 products" summary.
    val product_count: Int = 0,
)

// No property defaults: the app's Json has encodeDefaults=false, so a defaulted
// field is OMITTED from the body — and the server 422s without deletion_batch_id.
@Serializable
data class DeleteShelfRequest(
    val strategy: String?,
    val target_shelf_id: Long?,
    val deletion_batch_id: String,
)

@Serializable
data class UpdateShelfRequest(
    val name: String,
)

@Serializable
data class ReorderRequest(
    val ids: List<Long>,
)
```

In `data/dto/LocationDtos.kt`:

```kotlin
@Serializable
data class LocationDto(
    val id: Long,
    val name: String,
    val type: String,
    val position: Int? = null,
    // The server requires a delete STRATEGY when shelf_count > 0 — NOT when the
    // location merely holds products. Both sides read the same server relation
    // (shelvesWithContents), which counts any non-system shelf plus a system
    // "Unsorted" shelf that actually holds something. Decide `needsStrategy` from
    // THIS, or a location containing one empty shelf 422s on every delete.
    val shelf_count: Int = 0,
    // Total products across all the location's shelves. Feeds the dialog's
    // "2 locations · 17 products" summary.
    val product_count: Int = 0,
)

@Serializable
data class DeleteLocationRequest(
    val strategy: String?,
    val target_location_id: Long?,
    val deletion_batch_id: String,
)

@Serializable
data class UpdateLocationRequest(
    val name: String,
    val type: String,
)
```

In `data/dto/HouseholdDtos.kt` — replace `UpdateHouseholdThemeRequest` with one that also carries the name. Keep the no-defaults comment; it now guards three fields.

```kotlin
// No property defaults on purpose: the app's Json has encodeDefaults=false, so a
// defaulted field would be OMITTED from the body and the server would keep the
// old value instead of clearing it. Explicit null = clear back to the derived
// default. `name` is nullable so a theme-only change doesn't have to resend it.
@Serializable
data class UpdateHouseholdRequest(
    val name: String?,
    val color: String?,
    val icon: String?,
)
```

Create `data/dto/RestoreDtos.kt`:

```kotlin
package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RestoreResponse(
    val message: String,
    val restored: Int,
)
```

- [ ] **Step 4: Add the strategy enums**

Create `data/hierarchy/DeleteStrategy.kt`:

```kotlin
package dev.scuttle.inventory.data.hierarchy

/**
 * What to do with a shelf's products when the shelf is deleted. The server
 * refuses to guess — a non-empty shelf deleted with no strategy is a 422.
 */
enum class ShelfDeleteStrategy(
    val wire: String,
) {
    MOVE_PRODUCTS("move_products"),
    UNSORT_PRODUCTS("unsort_products"),
    DELETE_PRODUCTS("delete_products"),
}

/**
 * What to do with a location's contents. There is deliberately no `unsort` here:
 * "unsorted" means off-shelf but still IN this location, and the location is the
 * thing being deleted.
 */
enum class LocationDeleteStrategy(
    val wire: String,
) {
    MOVE_CONTENTS("move_contents"),
    DELETE_CONTENTS("delete_contents"),
}
```

- [ ] **Step 5: Extend the Api interfaces**

`data/api/ShelfApi.kt` — Retrofit needs `@HTTP` (not `@DELETE`) to send a body on a DELETE:

```kotlin
import retrofit2.http.HTTP
import retrofit2.http.PATCH

    @PATCH("households/{household}/locations/{location}/shelves/{shelf}")
    suspend fun update(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Path("shelf") shelfId: Long,
        @Body body: UpdateShelfRequest,
    ): ShelfResponse

    @PATCH("households/{household}/locations/{location}/shelves/reorder")
    suspend fun reorder(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Body body: ReorderRequest,
    ): ShelfListResponse

    // @DELETE cannot carry a body; @HTTP(hasBody = true) can. The strategy and
    // the batch id have to travel with the request.
    @HTTP(method = "DELETE", path = "households/{household}/locations/{location}/shelves/{shelf}", hasBody = true)
    suspend fun delete(
        @Path("household") householdId: Long,
        @Path("location") locationId: Long,
        @Path("shelf") shelfId: Long,
        @Body body: DeleteShelfRequest,
    )
```

`data/api/LocationApi.kt` — the same three, one level up (`update` takes `UpdateLocationRequest`, `reorder` is `households/{household}/locations/reorder`, `delete` is `@HTTP(... hasBody = true)` with `DeleteLocationRequest`).

`data/api/HouseholdApi.kt` — rename `updateTheme` to `update` and take the new body:

```kotlin
    @PATCH("households/{household}")
    suspend fun update(
        @Path("household") householdId: Long,
        @Body body: UpdateHouseholdRequest,
    ): HouseholdResponse
```

Create `data/api/RestoreApi.kt`:

```kotlin
package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.RestoreResponse
import retrofit2.http.POST
import retrofit2.http.Path

interface RestoreApi {
    @POST("households/{household}/restore/{batch}")
    suspend fun restore(
        @Path("household") householdId: Long,
        @Path("batch") batchId: String,
    ): RestoreResponse
}
```

- [ ] **Step 6: Extend the repositories**

In `data/shelf/ShelfRepository.kt`, add the new methods **with throwing defaults** so the existing fakes still compile:

```kotlin
    /**
     * Defaults throw so test fakes only implement what a test actually exercises
     * (same pattern as [clear] and HouseholdRepository.updateTheme). Without
     * this, adding a method here breaks every fake in the unit-test suite.
     */
    suspend fun rename(
        householdId: Long,
        locationId: Long,
        shelfId: Long,
        name: String,
    ): ShelfDto = throw UnsupportedOperationException("rename not supported")

    suspend fun reorder(
        householdId: Long,
        locationId: Long,
        ids: List<Long>,
    ): List<ShelfDto> = throw UnsupportedOperationException("reorder not supported")

    suspend fun deleteWithStrategy(
        householdId: Long,
        locationId: Long,
        shelfId: Long,
        batchId: String,
        strategy: ShelfDeleteStrategy?,
        targetShelfId: Long?,
    ): Unit = throw UnsupportedOperationException("deleteWithStrategy not supported")
```

> **Why a new name rather than changing `delete`'s signature:** `delete(householdId, locationId, shelfId)` is called from existing code and implemented by existing fakes. Adding `deleteWithStrategy` alongside it keeps this task additive; Task 5 switches the call sites over, and the old `delete` is removed in Task 10.

Implement all three in `ShelfRepositoryImpl`, keeping the cache in sync exactly as the existing methods do:

```kotlin
        override suspend fun rename(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
            name: String,
        ): ShelfDto =
            api.update(householdId, locationId, shelfId, UpdateShelfRequest(name = name)).data.also { updated ->
                val key = householdId to locationId
                cache[key] = cache[key]?.map { if (it.id == shelfId) updated else it } ?: listOf(updated)
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
            batchId: String,
            strategy: ShelfDeleteStrategy?,
            targetShelfId: Long?,
        ) {
            api.delete(
                householdId,
                locationId,
                shelfId,
                DeleteShelfRequest(
                    strategy = strategy?.wire,
                    target_shelf_id = targetShelfId,
                    deletion_batch_id = batchId,
                ),
            )
            val key = householdId to locationId
            cache[key] = cache[key]?.filter { it.id != shelfId } ?: emptyList()
        }
```

Mirror all of this in `LocationRepository` / `LocationRepositoryImpl` (`rename(householdId, locationId, name, type)`, `reorder(householdId, ids)`, `deleteWithStrategy(householdId, locationId, batchId, strategy, targetLocationId)`).

In `HouseholdRepository` / `HouseholdRepositoryImpl`, replace `updateTheme` with:

```kotlin
    suspend fun update(
        householdId: Long,
        name: String?,
        color: String?,
        icon: String?,
    ): HouseholdDto = throw UnsupportedOperationException("update not supported")
```

Create `data/hierarchy/RestoreRepository.kt` + `RestoreRepositoryImpl.kt` following the same interface/impl shape (no cache needed).

- [ ] **Step 7: Register the new Api in BOTH network modules**

In `di/NetworkModule.kt`:

```kotlin
    @Provides
    @Singleton
    fun provideRestoreApi(retrofit: Retrofit): RestoreApi = retrofit.create(RestoreApi::class.java)
```

**And the same `@Provides` in `app/src/androidTest/java/dev/scuttle/inventory/di/TestNetworkModule.kt`.** Miss this and every flow test fails to inject with an unhelpful Hilt error.

Bind `RestoreRepository` in `di/RepositoryModule.kt`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindRestoreRepository(impl: RestoreRepositoryImpl): RestoreRepository
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*DeleteRequestSerializationTest*'`
Expected: PASS, 2 tests.

- [ ] **Step 9: Fix the one existing caller of `updateTheme`**

`ui/households/HouseholdsViewModel.kt` calls `repository.updateTheme(id, color, icon)`. Change it to `repository.update(id, name = null, color = color, icon = icon)`.

- [ ] **Step 10: Run the full gate**

Run: `./gradlew testDebugUnitTest ktlintCheck detekt`
Expected: all PASS.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/data app/src/main/java/dev/scuttle/inventory/di app/src/androidTest/java/dev/scuttle/inventory/di app/src/test/java/dev/scuttle/inventory/DeleteRequestSerializationTest.kt
git commit -m "feat: data layer for renaming, reordering and safe deletion

Adds rename/reorder/deleteWithStrategy to the location and shelf repositories,
name to the household update, and a restore-by-batch endpoint.

DELETE now carries a body (@HTTP hasBody, since @DELETE cannot), because the
strategy and the client-minted batch id have to travel with the request. The
request DTOs deliberately have no property defaults: encodeDefaults=false
would silently drop them and the server would 422."
```

---

## Task 2: The ordering rule

Pure logic, fully unit-testable, no UI. Gets the contentious part right before anything renders it.

**Files:**
- Create: `ui/common/HierarchyOrder.kt`
- Test: `app/src/test/java/dev/scuttle/inventory/HierarchyOrderTest.kt`

**Interfaces:**
- Produces: `fun <T> orderByPosition(items: List<T>, position: (T) -> Int?, name: (T) -> String): List<T>`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/dev/scuttle/inventory/HierarchyOrderTest.kt`:

```kotlin
package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.ui.common.orderByPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class HierarchyOrderTest {
    private fun shelf(
        id: Long,
        name: String,
        position: Int?,
        isSystem: Boolean = false,
    ) = ShelfDto(id = id, name = name, position = position, location_id = 1L, is_system = isSystem)

    private fun order(items: List<ShelfDto>) = orderByPosition(items, { it.position }, { it.name })

    @Test
    fun manual_position_wins() {
        val items =
            listOf(
                shelf(1, "Top", 0),
                shelf(2, "Middle", 1),
                shelf(3, "Bottom", 2),
            )

        assertEquals(listOf(1L, 2L, 3L), order(items.shuffled()).map { it.id })
    }

    @Test
    fun a_star_never_reorders_anything() {
        // THE rule. Shelf order is physical: if starring the bottom shelf floated
        // it to the top, the list would stop matching the fridge the user is
        // standing in front of. A star is a marker and a filter, never a sort —
        // so this function does not even take a favourites set as an argument.
        val items =
            listOf(
                shelf(1, "Top", 0),
                shelf(2, "Middle", 1),
                shelf(3, "Bottom", 2),
            )

        // Whatever the caller has starred, the order is unchanged.
        assertEquals(listOf(1L, 2L, 3L), order(items).map { it.id })
    }

    @Test
    fun name_is_only_the_tie_break_for_never_reordered_items() {
        // Everything sits at position 0 until someone drags. Falling back to name
        // keeps a fresh list stable instead of ordering it by insertion id.
        val items =
            listOf(
                shelf(1, "Zebra", 0),
                shelf(2, "Apple", 0),
                shelf(3, "Mango", 0),
            )

        assertEquals(listOf("Apple", "Mango", "Zebra"), order(items).map { it.name })
    }

    @Test
    fun a_null_position_sorts_as_zero() {
        // An older server, or a row created before the position column existed.
        val items =
            listOf(
                shelf(1, "Bbb", null),
                shelf(2, "Aaa", null),
                shelf(3, "Ccc", 1),
            )

        assertEquals(listOf("Aaa", "Bbb", "Ccc"), order(items).map { it.name })
    }

    @Test
    fun the_sort_is_stable_across_repeated_calls() {
        val items = listOf(shelf(1, "Same", 0), shelf(2, "Same", 0))

        assertEquals(order(items).map { it.id }, order(order(items)).map { it.id })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*HierarchyOrderTest*'`
Expected: FAIL — unresolved reference `orderByPosition`.

- [ ] **Step 3: Write the implementation**

Create `ui/common/HierarchyOrder.kt`:

```kotlin
package dev.scuttle.inventory.ui.common

/**
 * The one ordering rule for every hierarchy list in the app.
 *
 * 1. Manual position — what the user set by moving rows. It always wins.
 * 2. Name — the tie-break for items nobody has reordered yet (they all sit at 0).
 *
 * A star is deliberately NOT an input here. Starring is a marker and a filter,
 * never a sort: shelf order is physical, and a star that floated the bottom shelf
 * to the top of the list would leave the app disagreeing with the actual fridge.
 * Keeping favourites out of this signature makes that impossible to get wrong.
 */
fun <T> orderByPosition(
    items: List<T>,
    position: (T) -> Int?,
    name: (T) -> String,
): List<T> =
    items.sortedWith(
        compareBy<T> { position(it) ?: 0 }
            .thenBy { name(it).lowercase() },
    )
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests '*HierarchyOrderTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/common/HierarchyOrder.kt app/src/test/java/dev/scuttle/inventory/HierarchyOrderTest.kt
git commit -m "feat: one ordering rule for every hierarchy list

Manual position wins; name is only the tie-break for never-reordered items.
A star is deliberately not an input: it is a marker and a filter, never a
sort. Shelf order is physical, and a star that floated the bottom shelf to the
top would leave the list disagreeing with the actual fridge."
```

---

## Task 3: The delete-strategy dialog

The user-facing half of the safety fix. Built and tested before it is wired to anything.

**Files:**
- Create: `ui/hierarchy/DeleteStrategyDialog.kt`, `ui/hierarchy/DeletePlan.kt`
- Modify: `res/values/strings.xml`, `res/values-nl/strings.xml`
- Test: `app/src/test/java/dev/scuttle/inventory/DeletePlanTest.kt`

**Interfaces:**
- Produces: `data class DeletePlan(val shelfIds, val locationIds, val productCount, val availableTargets)`; `@Composable fun DeleteStrategyDialog(plan, onDismiss, onConfirm: (ShelfDeleteStrategy?, Long?) -> Unit)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/dev/scuttle/inventory/DeletePlanTest.kt`:

```kotlin
package dev.scuttle.inventory

import dev.scuttle.inventory.ui.hierarchy.DeletePlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletePlanTest {
    @Test
    fun an_empty_selection_needs_no_strategy() {
        val plan = DeletePlan(itemCount = 2, productCount = 0, contentCount = 0, hasOtherTargets = true)

        // Nothing to rescue — a plain confirm is enough.
        assertFalse(plan.needsStrategy)
    }

    @Test
    fun a_shelf_holding_products_needs_a_strategy() {
        // For a SHELF, contentCount is its product_count.
        val plan = DeletePlan(itemCount = 1, productCount = 12, contentCount = 12, hasOtherTargets = true)

        assertTrue(plan.needsStrategy)
    }

    @Test
    fun a_location_holding_an_EMPTY_shelf_still_needs_a_strategy() {
        // THE cross-repo trap. The server asks for a strategy when a location has
        // SHELVES, not when it has products — so a location with one empty shelf
        // (0 products, 1 shelf) must still prompt. Deciding this from productCount
        // would send a strategy-less delete and 422 every single time.
        val plan = DeletePlan(itemCount = 1, productCount = 0, contentCount = 1, hasOtherTargets = true)

        assertTrue(plan.needsStrategy)
    }

    @Test
    fun move_is_unavailable_when_there_is_nowhere_to_move_to() {
        // Deleting the household's only location: there is no other location to
        // take the contents, so the UI must not offer "move" as a dead option.
        val plan = DeletePlan(itemCount = 1, productCount = 12, contentCount = 12, hasOtherTargets = false)

        assertTrue(plan.needsStrategy)
        assertFalse(plan.canMove)
    }

    @Test
    fun move_is_available_when_a_target_exists() {
        val plan = DeletePlan(itemCount = 1, productCount = 12, contentCount = 12, hasOtherTargets = true)

        assertTrue(plan.canMove)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DeletePlanTest*'`
Expected: FAIL — unresolved reference `DeletePlan`.

- [ ] **Step 3: Write the plan model**

Create `ui/hierarchy/DeletePlan.kt`:

```kotlin
package dev.scuttle.inventory.ui.hierarchy

/**
 * What one delete gesture is about to destroy, and therefore what the user has
 * to be asked.
 *
 * A BATCH gets ONE dialog, not one per item: selecting three shelves and being
 * interrogated three times is worse than being told "3 shelves · 17 products"
 * once and choosing once.
 */
data class DeletePlan(
    /** How many containers (shelves or locations) are selected. */
    val itemCount: Int,
    /** How many products live inside them, in total. Feeds the summary line. */
    val productCount: Int,
    /**
     * What the SERVER counts as "has contents" for the thing being deleted.
     *
     * These two are NOT the same rule, and getting it wrong 422s every delete:
     *  - deleting a SHELF    → the server asks when the shelf has PRODUCTS.
     *  - deleting a LOCATION → the server asks when the location has SHELVES
     *                          (`shelf_count`), even if those shelves are empty.
     *
     * So the caller passes the right count: `product_count` for shelves,
     * `shelf_count` for locations. Do not "simplify" this to productCount.
     */
    val contentCount: Int,
    /** Is there anywhere else to put the contents? False when this is the only shelf/location. */
    val hasOtherTargets: Boolean,
) {
    /** An empty container is safe to delete outright — a plain confirm will do. */
    val needsStrategy: Boolean get() = contentCount > 0

    /** Never offer "move" when there is nothing to move to. */
    val canMove: Boolean get() = hasOtherTargets
}
```

- [ ] **Step 4: Add the strings (EN + NL)**

`res/values/strings.xml`:

```xml
    <!-- Delete strategy -->
    <string name="delete_strategy_title">Delete %1$d item(s)?</string>
    <string name="delete_strategy_summary">%1$d product(s) are stored inside. What should happen to them?</string>
    <string name="delete_strategy_move">Move them somewhere else</string>
    <string name="delete_strategy_unsort">Keep them here, in Unsorted</string>
    <string name="delete_strategy_delete">Delete them too</string>
    <string name="delete_strategy_pick_target">Where should they go?</string>
    <string name="delete_strategy_no_target">There is nowhere else to move them.</string>
    <string name="delete_undo">Undo</string>
    <string name="delete_undone">Restored.</string>
    <string name="delete_undo_failed">Couldn\'t undo — this was already restored or permanently removed.</string>
    <string name="shelf_unsorted">Unsorted</string>
```

`res/values-nl/strings.xml` (same keys, same order):

```xml
    <!-- Delete strategy -->
    <string name="delete_strategy_title">%1$d item(s) verwijderen?</string>
    <string name="delete_strategy_summary">Er liggen %1$d product(en) in. Wat moet daarmee gebeuren?</string>
    <string name="delete_strategy_move">Verplaats ze ergens anders heen</string>
    <string name="delete_strategy_unsort">Bewaar ze hier, in Ongesorteerd</string>
    <string name="delete_strategy_delete">Verwijder ze ook</string>
    <string name="delete_strategy_pick_target">Waar moeten ze heen?</string>
    <string name="delete_strategy_no_target">Er is geen andere plek om ze heen te verplaatsen.</string>
    <string name="delete_undo">Ongedaan maken</string>
    <string name="delete_undone">Hersteld.</string>
    <string name="delete_undo_failed">Ongedaan maken lukt niet — dit is al hersteld of definitief verwijderd.</string>
    <string name="shelf_unsorted">Ongesorteerd</string>
```

- [ ] **Step 5: Write the dialog**

Create `ui/hierarchy/DeleteStrategyDialog.kt`. Follow the `AlertDialog` shape used by `HouseholdThemeDialog` — `title` / `text` / `confirmButton` / `dismissButton`, error-coloured confirm, `testTag` on each option.

```kotlin
package dev.scuttle.inventory.ui.hierarchy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.hierarchy.ShelfDeleteStrategy

/**
 * Asks what to do with the products inside the shelves being deleted.
 *
 * One dialog per GESTURE, not per item: the summary covers the whole batch and
 * one choice applies to all of it.
 */
@Composable
fun DeleteStrategyDialog(
    plan: DeletePlan,
    targets: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onConfirm: (ShelfDeleteStrategy?, Long?) -> Unit,
) {
    // Default to the safest option that is actually available. Never default to
    // destroying data.
    var strategy by remember {
        mutableStateOf(
            if (plan.canMove) ShelfDeleteStrategy.MOVE_PRODUCTS else ShelfDeleteStrategy.UNSORT_PRODUCTS,
        )
    }
    var targetId by remember { mutableStateOf(targets.firstOrNull()?.first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_strategy_title, plan.itemCount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.delete_strategy_summary, plan.productCount))

                if (plan.canMove) {
                    StrategyOption(
                        label = stringResource(R.string.delete_strategy_move),
                        selected = strategy == ShelfDeleteStrategy.MOVE_PRODUCTS,
                        tag = "delete-strategy-move",
                        onSelect = { strategy = ShelfDeleteStrategy.MOVE_PRODUCTS },
                    )
                } else {
                    Text(stringResource(R.string.delete_strategy_no_target))
                }

                StrategyOption(
                    label = stringResource(R.string.delete_strategy_unsort),
                    selected = strategy == ShelfDeleteStrategy.UNSORT_PRODUCTS,
                    tag = "delete-strategy-unsort",
                    onSelect = { strategy = ShelfDeleteStrategy.UNSORT_PRODUCTS },
                )

                StrategyOption(
                    label = stringResource(R.string.delete_strategy_delete),
                    selected = strategy == ShelfDeleteStrategy.DELETE_PRODUCTS,
                    tag = "delete-strategy-delete",
                    onSelect = { strategy = ShelfDeleteStrategy.DELETE_PRODUCTS },
                )

                if (strategy == ShelfDeleteStrategy.MOVE_PRODUCTS && targets.isNotEmpty()) {
                    Text(stringResource(R.string.delete_strategy_pick_target))
                    targets.forEach { (id, name) ->
                        StrategyOption(
                            label = name,
                            selected = targetId == id,
                            tag = "delete-target-$id",
                            onSelect = { targetId = id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        strategy,
                        if (strategy == ShelfDeleteStrategy.MOVE_PRODUCTS) targetId else null,
                    )
                },
                enabled = strategy != ShelfDeleteStrategy.MOVE_PRODUCTS || targetId != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("delete-strategy-confirm"),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun StrategyOption(
    label: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier =
            Modifier
                .selectable(selected = selected, onClick = onSelect)
                .testTag(tag),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
```

- [ ] **Step 6: Run the tests and the style gate**

Run: `./gradlew testDebugUnitTest --tests '*DeletePlanTest*' && ./gradlew ktlintCheck detekt`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/hierarchy app/src/main/res/values/strings.xml app/src/main/res/values-nl/strings.xml app/src/test/java/dev/scuttle/inventory/DeletePlanTest.kt
git commit -m "feat: delete-strategy dialog

Asks what to do with the products inside a container being deleted: move them,
keep them in Unsorted, or delete them too. One dialog per gesture — a batch of
three shelves is summarised once, not interrogated three times.

Defaults to the safest available option; never defaults to destroying data,
and never offers 'move' when there is nowhere to move to."
```

---

## Task 4: Edit mode on the shelves list (LocationDetail)

The hardest screen, and the one with today's dangerous no-confirmation delete. Includes the tabs⇄list toggle.

**Files:**
- Modify: `ui/shelves/ShelvesViewModel.kt`, `ui/location/LocationDetailScreen.kt`
- Create: `data/settings/ShelfViewStore.kt`, `data/settings/SharedPrefsShelfViewStore.kt`, `ui/hierarchy/EditableRow.kt`
- Modify: `di/StorageModule.kt`, `data/auth/SessionCleaner.kt`, `res/values/strings.xml`, `res/values-nl/strings.xml`
- Test: `app/src/test/java/dev/scuttle/inventory/ShelvesViewModelTest.kt` (extend)

**Interfaces:**
- Consumes: `ShelfRepository.rename/reorder/deleteWithStrategy` (Task 1), `orderByPosition` (Task 2), `DeleteStrategyDialog` + `DeletePlan` (Task 3).
- Produces: `ShelvesUiState` gains `editMode: Boolean`, `selected: Set<Long>`, `pendingDelete: DeletePlan?`, `lastBatchId: String?`, `listView: Boolean`. `ShelvesViewModel` gains `enterEditMode()`, `exitEditMode()`, `toggleSelection(id)`, `moveUp(id)`, `moveDown(id)`, `rename(id, name)`, `requestDelete()`, `confirmDelete(strategy, targetId)`, `undoDelete()`, `toggleListView()`.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/dev/scuttle/inventory/ShelvesViewModelTest.kt`. Extend `FakeShelfRepository` first to implement the new methods:

```kotlin
        override suspend fun rename(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
            name: String,
        ): ShelfDto {
            val index = items.indexOfFirst { it.id == shelfId }
            val updated = items[index].copy(name = name)
            items[index] = updated
            return updated
        }

        override suspend fun reorder(
            householdId: Long,
            locationId: Long,
            ids: List<Long>,
        ): List<ShelfDto> {
            val byId = items.associateBy { it.id }
            val reordered = ids.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) }
            items.clear()
            items.addAll(reordered)
            return reordered
        }

        var lastStrategy: ShelfDeleteStrategy? = null
        var lastBatchId: String? = null

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            shelfId: Long,
            batchId: String,
            strategy: ShelfDeleteStrategy?,
            targetShelfId: Long?,
        ) {
            lastStrategy = strategy
            lastBatchId = batchId
            items.removeIf { it.id == shelfId }
        }
```

Then the tests:

```kotlin
    @Test
    fun move_up_swaps_a_shelf_with_the_one_above_it() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Middle", 1, 1L))
                    items.add(ShelfDto(3, "Bottom", 2, 1L))
                }
            val viewModel = ShelvesViewModel(repo)
            viewModel.load(householdId = 1, locationId = 1)

            viewModel.moveUp(3L)

            assertEquals(
                listOf("Top", "Bottom", "Middle"),
                viewModel.state.value.shelves.map { it.name },
            )
        }

    @Test
    fun move_up_on_the_first_shelf_does_nothing() =
        runTest {
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val viewModel = ShelvesViewModel(repo)
            viewModel.load(householdId = 1, locationId = 1)

            viewModel.moveUp(1L)

            assertEquals(listOf("Top"), viewModel.state.value.shelves.map { it.name })
        }

    @Test
    fun the_unsorted_shelf_cannot_be_selected_for_deletion() =
        runTest {
            // It is a system shelf: it holds the products the user chose to KEEP.
            // Letting a stray checkbox tap destroy it would defeat the entire point.
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Unsorted", 1, 1L, is_system = true))
                }
            val viewModel = ShelvesViewModel(repo)
            viewModel.load(householdId = 1, locationId = 1)

            viewModel.enterEditMode()
            viewModel.toggleSelection(2L)

            assertTrue(viewModel.state.value.selected.isEmpty())
        }

    @Test
    fun confirming_a_delete_sends_the_chosen_strategy_and_one_batch_id() =
        runTest {
            val repo =
                FakeShelfRepository().apply {
                    items.add(ShelfDto(1, "Top", 0, 1L))
                    items.add(ShelfDto(2, "Bottom", 1, 1L))
                }
            val viewModel = ShelvesViewModel(repo)
            viewModel.load(householdId = 1, locationId = 1)

            viewModel.enterEditMode()
            viewModel.toggleSelection(1L)
            viewModel.toggleSelection(2L)
            viewModel.requestDelete()
            viewModel.confirmDelete(ShelfDeleteStrategy.DELETE_PRODUCTS, targetId = null)

            assertEquals(ShelfDeleteStrategy.DELETE_PRODUCTS, repo.lastStrategy)
            // Both shelves share ONE batch id, so one Undo brings both back.
            assertNotNull(repo.lastBatchId)
            assertEquals(repo.lastBatchId, viewModel.state.value.lastBatchId)
            assertTrue(viewModel.state.value.shelves.isEmpty())
        }

    @Test
    fun entering_edit_mode_forces_the_list_view() =
        runTest {
            // Tabs cannot host reorder buttons or inline rename. The flip is also
            // why the manual list/tab toggle exists: by the time the user first
            // enters edit mode, the list is somewhere they have already been.
            val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
            val viewModel = ShelvesViewModel(repo)
            viewModel.load(householdId = 1, locationId = 1)
            assertFalse(viewModel.state.value.listView)

            viewModel.enterEditMode()
            assertTrue(viewModel.state.value.listView)

            viewModel.exitEditMode()
            assertFalse(viewModel.state.value.listView)
        }
```

Add the imports `org.junit.Assert.assertNotNull` and `dev.scuttle.inventory.data.hierarchy.ShelfDeleteStrategy`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*ShelvesViewModelTest*'`
Expected: FAIL — unresolved references `moveUp`, `enterEditMode`, `selected`, `listView`.

- [ ] **Step 3: Add the shelf-view preference store**

Create `data/settings/ShelfViewStore.kt`:

```kotlin
package dev.scuttle.inventory.data.settings

/** Whether the shelves screen shows tabs+products or a plain shelf list. Global, not per-location. */
interface ShelfViewStore {
    fun isListView(): Boolean

    fun setListView(listView: Boolean)

    fun clear() {}
}
```

Create `data/settings/SharedPrefsShelfViewStore.kt` (same shape as `SharedPrefsThemeModeStore`, sharing the `"inventory_settings"` prefs file, key `"shelf_list_view"`, default `false`).

Register in `di/StorageModule.kt`:

```kotlin
    @Provides
    @Singleton
    fun provideShelfViewStore(
        @ApplicationContext context: Context,
    ): ShelfViewStore = SharedPrefsShelfViewStore(context)
```

**Wire it into `data/auth/SessionCleaner.clear()`** alongside the other stores.

- [ ] **Step 4: Extend `ShelvesViewModel`**

Add to `ShelvesUiState` and replace the old delete-mode block. Note `deleteMode`/`selectedShelves`/`deleteSelected`/`enterDeleteMode`/`exitDeleteMode` are **removed** — edit mode supersedes them.

```kotlin
data class ShelvesUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val shelves: List<ShelfDto> = emptyList(),
    val newName: String = "",
    val error: String? = null,
    val editMode: Boolean = false,
    val selected: Set<Long> = emptySet(),
    val listView: Boolean = false,
    /** Non-null while the strategy dialog is open. */
    val pendingDelete: DeletePlan? = null,
    /** The batch just deleted, for the Undo snackbar. Cleared once consumed. */
    val lastBatchId: String? = null,
)
```

And the ViewModel body — inject `ShelfViewStore`, `RestoreRepository`, and `HierarchyStore`:

```kotlin
@HiltViewModel
class ShelvesViewModel
    @Inject
    constructor(
        private val repository: ShelfRepository,
        private val restoreRepository: RestoreRepository,
        private val shelfViewStore: ShelfViewStore,
        private val hierarchyStore: HierarchyStore,
    ) : ViewModel() {
        // ... householdId / locationId / _state as before, but seed listView:
        private val _state = MutableStateFlow(ShelvesUiState(listView = shelfViewStore.isListView()))

        /** Remembered so exiting edit mode restores the view the user actually chose. */
        private var viewBeforeEdit: Boolean = false

        fun toggleListView() {
            val next = !_state.value.listView
            shelfViewStore.setListView(next)
            _state.update { it.copy(listView = next) }
        }

        fun enterEditMode() {
            // Tabs cannot host reorder buttons or an inline rename target, so edit
            // mode always runs in the list view — and restores the user's choice
            // on the way out.
            viewBeforeEdit = _state.value.listView
            _state.update { it.copy(editMode = true, listView = true, selected = emptySet()) }
        }

        fun exitEditMode() =
            _state.update {
                it.copy(editMode = false, listView = viewBeforeEdit, selected = emptySet(), pendingDelete = null)
            }

        fun toggleSelection(shelfId: Long) =
            _state.update { state ->
                // The Unsorted shelf holds the products the user chose to KEEP.
                // A stray checkbox tap must not be able to destroy it.
                val shelf = state.shelves.firstOrNull { it.id == shelfId }
                if (shelf == null || shelf.is_system) {
                    state
                } else {
                    val updated =
                        if (shelfId in state.selected) state.selected - shelfId else state.selected + shelfId
                    state.copy(selected = updated)
                }
            }

        fun rename(
            shelfId: Long,
            name: String,
        ) {
            val h = householdId ?: return
            val l = locationId ?: return
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            launchLoading {
                val updated = repository.rename(h, l, shelfId, trimmed)
                _state.update { s -> s.copy(shelves = s.shelves.map { if (it.id == shelfId) updated else it }) }
                hierarchyStore.refresh()
            }
        }

        fun moveUp(shelfId: Long) = move(shelfId, -1)

        fun moveDown(shelfId: Long) = move(shelfId, +1)

        private fun move(
            shelfId: Long,
            delta: Int,
        ) {
            val h = householdId ?: return
            val l = locationId ?: return
            val current = _state.value.shelves.filter { !it.is_system }
            val index = current.indexOfFirst { it.id == shelfId }
            val target = index + delta
            if (index < 0 || target !in current.indices) return

            val reordered = current.toMutableList().apply { add(target, removeAt(index)) }

            // Optimistic: the row visibly moves on tap. The server call rewrites
            // every position in one transaction, so a failure snaps the whole list
            // back rather than leaving it half-sorted.
            _state.update { it.copy(shelves = reordered + it.shelves.filter { s -> s.is_system }) }

            launchLoading {
                val server = repository.reorder(h, l, reordered.map { it.id })
                _state.update { it.copy(shelves = server) }
                hierarchyStore.refresh()
            }
        }

        /** Open the strategy dialog for the current selection. */
        fun requestDelete() {
            val state = _state.value
            val selected = state.shelves.filter { it.id in state.selected }
            if (selected.isEmpty()) return

            // productCount comes from the shelves' own product counts once the API
            // exposes them; until then the dialog asks whenever anything is selected
            // and the server is the backstop (it 422s a strategy-less delete).
            _state.update {
                it.copy(
                    pendingDelete =
                        DeletePlan(
                            itemCount = selected.size,
                            productCount = selected.sumOf { s -> productCountFor(s.id) },
                            hasOtherTargets = state.shelves.any { s -> s.id !in state.selected && !s.is_system },
                        ),
                )
            }
        }

        fun confirmDelete(
            strategy: ShelfDeleteStrategy?,
            targetId: Long?,
        ) {
            val h = householdId ?: return
            val l = locationId ?: return
            val ids = _state.value.selected.toList()
            if (ids.isEmpty()) return

            // ONE batch id for the whole gesture. Deleting three shelves is three
            // requests; if each minted its own id they would land in three batches
            // and Undo would restore only one of them.
            val batchId = UUID.randomUUID().toString()

            launchLoading {
                ids.forEach { id -> repository.deleteWithStrategy(h, l, id, batchId, strategy, targetId) }
                _state.update {
                    it.copy(
                        editMode = false,
                        selected = emptySet(),
                        pendingDelete = null,
                        lastBatchId = batchId,
                        shelves = it.shelves.filter { s -> s.id !in ids },
                    )
                }
                hierarchyStore.refresh()
            }
        }

        fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

        fun undoDelete() {
            val h = householdId ?: return
            val batchId = _state.value.lastBatchId ?: return
            launchLoading {
                restoreRepository.restore(h, batchId)
                _state.update { it.copy(lastBatchId = null) }
                refresh()
                hierarchyStore.refresh()
            }
        }

        fun consumeLastBatch() = _state.update { it.copy(lastBatchId = null) }
```

> **`productCountFor(id)`**: the shelves list endpoint does not return a product count today. Add `product_count` to `ShelfResource` on the backend (one line in `toArray()`), surface it as `ShelfDto.product_count: Int = 0`, and read it here. Do that as part of this step — the dialog's summary is a lie without it.

- [ ] **Step 5: Rebuild `LocationDetailScreen`**

Replace the delete-mode top bar with an edit-mode one, add the view toggle, and render either the tabs+pager (as today) or a shelf list. The shelf list row is `EditableRow` (create `ui/hierarchy/EditableRow.kt` — a `FrostCard` with an optional leading `Checkbox`, the name, and trailing `IconButton`s for up/down, all gated on `editMode`). Wire the dialog and the snackbar:

```kotlin
    // The strategy dialog for the current selection.
    state.pendingDelete?.let { plan ->
        DeleteStrategyDialog(
            plan = plan,
            targets =
                state.shelves
                    .filter { it.id !in state.selected && !it.is_system }
                    .map { it.id to it.name },
            onDismiss = shelvesViewModel::cancelDelete,
            onConfirm = { strategy, targetId -> shelvesViewModel.confirmDelete(strategy, targetId) },
        )
    }

    // Undo. A snackbar with an action, rather than SnackbarErrorEffect (which is
    // for one-shot errors and has no action slot).
    val undoLabel = stringResource(R.string.delete_undo)
    LaunchedEffect(state.lastBatchId) {
        val batch = state.lastBatchId ?: return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            shelvesViewModel.undoDelete()
        } else {
            shelvesViewModel.consumeLastBatch()
        }
    }
```

Top bar actions become: view-toggle `IconButton` (list ⇄ tabs, hidden in edit mode), pencil `IconButton` → `enterEditMode()`, and `+` → add sheet. In edit mode: Cancel `TextButton` in the navigation slot, and an error-coloured Delete `Button` enabled when `selected.isNotEmpty()`.

New strings (EN + NL): `location_edit_shelves_cd`, `location_view_toggle_cd`, `shelf_move_up_cd`, `shelf_move_down_cd`, `shelf_rename_title`, `shelves_deleted`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*ShelvesViewModelTest*'`
Expected: PASS — the 5 existing tests (minus the two removed delete-mode ones, which are replaced) plus the 5 new ones.

- [ ] **Step 7: Run the full gate**

Run: `./gradlew testDebugUnitTest ktlintCheck detekt`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory app/src/main/res app/src/test/java/dev/scuttle/inventory/ShelvesViewModelTest.kt
git commit -m "feat: edit mode on the shelves list, and a tabs/list toggle

Replaces the bulk delete mode — which deleted checked shelves with NO
confirmation dialog at all — with an edit mode that renames, reorders, and
deletes via the strategy dialog, with snackbar undo.

Edit mode always runs in the list view, because a tab strip cannot host
reorder buttons or an inline rename. The manual list/tab toggle exists so
that by the time a user first enters edit mode, the list is somewhere they
have already been.

The Unsorted shelf cannot be selected: it holds the products the user chose
to keep."
```

---

## Task 5: Edit mode on the locations list (StorageOverview)

**Files:**
- Modify: `ui/storage/StorageOverviewViewModel.kt`, `ui/storage/StorageOverviewScreen.kt`
- Test: `app/src/test/java/dev/scuttle/inventory/StorageOverviewViewModelTest.kt` (extend)

**Interfaces:** mirrors Task 4 exactly — `enterEditMode()`, `exitEditMode()`, `toggleSelection(id)`, `moveUp/moveDown(id)`, `rename(id, name, type)`, `requestDelete()`, `confirmDelete(strategy, targetId)`, `undoDelete()`. The strategy enum is `LocationDeleteStrategy`, and **`canMove` is false when the household has only one location** — the dialog then offers only delete-or-cancel.

- [ ] **Step 1: Write the failing tests**

Append to `StorageOverviewViewModelTest.kt`. The key one:

```kotlin
    @Test
    fun deleting_the_only_location_offers_no_move_target() =
        runTest {
            // There is nowhere to move the contents to, so the dialog must not
            // dangle a "move" option that cannot work.
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Fridge", "fridge", 0)) }
            val viewModel = StorageOverviewViewModel(repo, FakeHierarchyStore(), FakeRestoreRepository())
            viewModel.load(householdId = 1)

            viewModel.enterEditMode()
            viewModel.toggleSelection(1L)
            viewModel.requestDelete()

            assertFalse(viewModel.state.value.pendingDelete!!.canMove)
        }

    @Test
    fun a_rename_updates_the_row_in_place() =
        runTest {
            val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Freezr", "freezer", 0)) }
            val viewModel = StorageOverviewViewModel(repo, FakeHierarchyStore(), FakeRestoreRepository())
            viewModel.load(householdId = 1)

            viewModel.rename(1L, "Freezer", "freezer")

            assertEquals("Freezer", viewModel.state.value.locations.first().name)
        }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*StorageOverviewViewModelTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Extend the ViewModel**

Same shape as Task 4. `StorageOverviewUiState` gains `editMode`, `selected`, `pendingDelete`, `lastBatchId`. Reuse `orderByPosition` when rendering. Keep the existing `deleteLocation(id)` **removed** in favour of the strategy flow — and remember that **every mutation must call `hierarchyStore.refresh()`** and use `launchLoading { }` with `refreshing = false`.

- [ ] **Step 4: Rewrite the screen's delete affordance**

**Remove the `SwipeToDismissBox`.** Delete now lives behind edit mode, which is the entire point — a swipe should not be able to destroy a fridge full of food. Rows become `EditableRow` (from Task 4). The pencil goes in the top bar; the edit sheet (name + type `FilterChip`s, reusing `STORAGE_TYPES`) opens on row tap in edit mode.

- [ ] **Step 5: Run the tests and the gate**

Run: `./gradlew testDebugUnitTest ktlintCheck detekt`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/storage app/src/test/java/dev/scuttle/inventory/StorageOverviewViewModelTest.kt
git commit -m "feat: edit mode on the locations list

Rename, retype, reorder and delete-with-strategy, behind the same pencil as
the shelves list.

Removes swipe-to-delete on locations: a swipe should not be able to destroy a
fridge full of food. Deletion now requires entering edit mode, selecting, and
answering the strategy dialog."
```

---

## Task 6: Household edit page

**Files:**
- Create: `ui/households/HouseholdEditScreen.kt`
- Modify: `ui/households/HouseholdsScreen.kt`, `ui/households/HouseholdsViewModel.kt`, `MainActivity.kt`
- Delete: `ui/households/HouseholdThemeDialog.kt` (its content moves into the edit screen)

- [ ] **Step 1: Write the failing test**

In `HouseholdsViewModelTest.kt`:

```kotlin
    @Test
    fun renaming_a_household_updates_it_in_place() =
        runTest {
            val repo = FakeHouseholdRepository().apply { items.add(HouseholdDto(1, "Huse", "AAAA-1111")) }
            val viewModel = HouseholdsViewModel(repo)

            viewModel.update(1L, name = "House", color = null, icon = null)

            assertEquals("House", viewModel.state.value.households.first().name)
        }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*HouseholdsViewModelTest*'`
Expected: FAIL — `update` unresolved.

- [ ] **Step 3: Build the edit screen**

`HouseholdEditScreen(householdId)` — a `Scaffold` + `Column(verticalScroll)`:

1. **Name** — `OutlinedTextField` + Save.
2. **Appearance** — the colour swatches and icon grid lifted verbatim from `HouseholdThemeDialog`, keeping the `testTag("theme-color-$key")` tags so the existing flow test still passes.
3. **Danger zone** — a visually separated section (an error-tinted `Card`, not a `FrostCard` — per `FrostCard`'s own doc comment, semantically-coloured cards stay on plain `Card`) containing **Leave household** with its existing confirm dialog.

Leaving is not an edit of the household — it ends *your membership*. It sits in a danger zone rather than next to the rename field so the two are never confused. There is still **no "delete household"**: nobody owns one. That arrives with roles (Spec 2), and this danger zone is where it will land — leave a comment saying so.

- [ ] **Step 4: Strip the household card down**

In `HouseholdsScreen.kt`, remove the palette `IconButton` and the Leave `TextButton` from the row. The card keeps the avatar, the name, and the share/invite icon. Add the pencil to the top bar; in edit mode, tapping a row navigates to `HouseholdEditScreen`.

- [ ] **Step 5: Add the route**

In `MainActivity.kt`, add `const val HOUSEHOLD_EDIT = "household-edit/{householdId}"` + a `householdEdit(id)` builder + the `composable(...)` block with a `NavType.LongType` argument, following the `Routes.LOCATION` pattern exactly.

- [ ] **Step 6: Run the tests and the gate**

Run: `./gradlew testDebugUnitTest ktlintCheck detekt`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/households app/src/main/java/dev/scuttle/inventory/MainActivity.kt app/src/test/java/dev/scuttle/inventory/HouseholdsViewModelTest.kt
git commit -m "feat: household edit page

Rename plus the colour/icon picker (moved out of the palette-icon dialog), and
a danger zone holding Leave.

Leave is not an edit of the household — it ends your membership — so it sits
in a separated danger zone rather than beside the rename field. Delete
household still does not exist: nobody owns one. That lands with roles."
```

---

## Task 7: Navigation rework

**Files:**
- Modify: `MainActivity.kt`, `ui/settings/SettingsScreen.kt`, `res/values/strings.xml`, `res/values-nl/strings.xml`
- Test: `app/src/androidTest/java/dev/scuttle/inventory/flow/*` (fix `bottom-nav-households` references)

- [ ] **Step 1: Change the tabs**

In `MainActivity.kt`, replace `bottomTabs`:

```kotlin
    val bottomTabs =
        listOf(
            BottomTab("dashboard", Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.SpaceDashboard),
            BottomTab("home", Routes.HOME, R.string.nav_storage, Icons.Filled.Home),
            // The centre slot is the primary-ACTION slot. Scanning is a weekly
            // grocery-trip action; search is an occasional "where did I put it",
            // and it already has a top-bar icon.
            BottomTab("scanner", Routes.SCANNER, R.string.nav_scan, Icons.Filled.QrCodeScanner),
            BottomTab("missing-items", Routes.MISSING_ITEMS, R.string.nav_missing_items, Icons.Filled.Warning),
            // Not "Settings": it now holds households, join/invite and account.
            BottomTab("more", Routes.SETTINGS, R.string.nav_more, Icons.Filled.MoreHoriz),
        )
```

Remove the `households` tab and the search-tab special cases (the `enabled = drawerUi.entries.isNotEmpty()` guard and the household-picker `ModalBottomSheet`) — Search is reached from the top bar now, where the household is already known.

Delete the `onOpenSettings` gear `IconButton` from every top-level screen's app bar; `More` replaces it.

- [ ] **Step 2: Add the strings**

`nav_scan` / `nav_more` in EN (`Scan`, `More`) and NL (`Scannen`, `Meer`).

- [ ] **Step 3: Fix the flow tests**

`grep -rl "bottom-nav-households" app/src/androidTest` — each hit navigates to Households via the tab. Change them to go through `bottom-nav-more` → the "My households" button.

- [ ] **Step 4: Run the gate**

Run: `./gradlew testDebugUnitTest ktlintCheck detekt`
Expected: all PASS. (Flow tests are nightly, not on PR — but run `./gradlew connectedDebugAndroidTest` locally if an emulator is available, since this task touches every one of them.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory app/src/main/res app/src/androidTest
git commit -m "feat: rework the bottom navigation

Households leaves the bar — it is a management screen, not a daily
destination, and the Storage tab already acts across households. Scan takes
the freed centre slot (the primary-action slot; scanning is a weekly action,
search an occasional one). Settings comes back into the bar as More, since it
now holds households, join/invite and account."
```

---

## Task 8: Collapsible household groups

**Files:**
- Create: `data/settings/HouseholdViewStore.kt`, `data/settings/SharedPrefsHouseholdViewStore.kt`
- Modify: `ui/home/AllStoragesScreen.kt`, `ui/home/AllStoragesViewModel.kt`, `di/StorageModule.kt`, `data/auth/SessionCleaner.kt`

**Interfaces:** `HouseholdViewStore.collapsed(): Set<Long>` / `toggleCollapsed(id)` / `order(): List<Long>` / `setOrder(ids)` / `clear()`.

Household order is **device-local** (D8): a household's *physical* structure is shared reality, but "which of my households do I care about" is a personal view preference. It never goes to the server.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/dev/scuttle/inventory/AllStoragesViewModelTest.kt`:

```kotlin
    @Test
    fun collapsing_a_household_persists() =
        runTest {
            val store = FakeHouseholdViewStore()
            val viewModel = AllStoragesViewModel(FakeFavoritesStore(), store)

            viewModel.toggleCollapsed(1L)

            assertTrue(1L in viewModel.state.value.collapsedHouseholdIds)
            assertTrue(1L in store.collapsed())
        }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*AllStoragesViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the store, the ViewModel state, and the collapsing header**

`SharedPrefsHouseholdViewStore` uses its own prefs file `"household_view"` (mirroring `SharedPrefsFavoritesStore`'s `"favorites"`), storing a `StringSet` of collapsed ids and an ordered id list. **Wire `clear()` into `SessionCleaner`.**

In `AllStoragesScreen`, the household header becomes clickable with a rotating chevron; the group's rows render only when not collapsed. Apply `orderByPosition` to the locations within each group, and the store's order to the groups themselves.

- [ ] **Step 4: Run the gate and commit**

```bash
git add app/src/main/java/dev/scuttle/inventory app/src/test/java/dev/scuttle/inventory/AllStoragesViewModelTest.kt
git commit -m "feat: collapsible household groups on the storage list

Collapse state and household order persist per device. A household's physical
structure is shared reality and lives on the server; which of your households
you care about is a personal view preference and stays local."
```

---

## Task 9: Flow tests for the delete-safety path

The regression net for the bug that started all this.

**Files:**
- Create: `app/src/androidTest/java/dev/scuttle/inventory/flow/DeleteShelfStrategyFlowTest.kt`, `.../RenameLocationFlowTest.kt`
- Create fixtures: `app/src/androidTest/assets/fixtures/shelves_two.json`, `shelf_renamed.json`, `location_renamed.json`, `restore_ok.json`
- Delete: the old swipe-to-delete assertions in `DeleteLocationFlowTest.kt` (the gesture no longer exists)

- [ ] **Step 1: Write the flow test**

`DeleteShelfStrategyFlowTest` — sign in, reach LocationDetail, enter edit mode, select a shelf, tap Delete, assert the **strategy dialog appears**, pick "Delete them too", confirm, assert the shelf is gone and the **Undo snackbar shows**. Follow `DeleteLocationFlowTest`'s structure exactly: `mockServer.route(path, fixture)` per endpoint, `Thread.sleep` + `waitForIdle()` + `waitUntilAtLeastOneExists`, assertions on literal EN strings.

- [ ] **Step 2: Rewrite `DeleteLocationFlowTest`**

The swipe gesture is gone (Task 5). Rewrite it to go through edit mode, and keep the name — it still guards location deletion.

- [ ] **Step 3: Run**

Run: `./gradlew connectedDebugAndroidTest` (needs an emulator; CI runs these nightly).
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest
git commit -m "test: flow tests for the delete-strategy path

Covers the bug the whole change exists to fix: deleting a shelf that holds
products now stops and asks, instead of destroying them silently."
```

---

## Task 10: Rewrite the contradicted rules and clean up

**Files:**
- Modify: `CLAUDE.md`, `ROADMAP.md`
- Delete: `ShelfRepository.delete` (the old 3-arg version) and its fakes' overrides

- [ ] **Step 1: Fix `CLAUDE.md`**

**Navigation section** — it still documents the old bar. Replace:

> Navigation: Household → Storage overview → Shelves (tabs) → Products. A 5-tab bottom bar (Dashboard / Storage / Households / Missing / Search) is the app's only navigation surface — there is no drawer. Settings is reached via a gear icon in each top-level screen's app bar.

with:

```markdown
Navigation: Household → Storage overview → Shelves (tabs *or* list — user toggle) →
Products. A 5-tab bottom bar (**Dashboard / Storage / Scan / Missing / More**) is the
app's only navigation surface — there is no drawer, and no gear icon. **More** is the
management hub: households, join/invite, language, theme, account. **Search** is reached
from the top bar of the screens that have a household in scope.

**Editing the hierarchy** lives behind a pencil (edit mode) on the households, locations
and shelves lists — never in Settings. In edit mode a row gets a checkbox (multi-select
delete) and move up/down buttons (reorder); tapping the row body opens its edit sheet.
Deleting a non-empty container always asks what to do with the contents.
```

**Scope guardrails** — remove `roles/permissions` from the refuse-to-add list; it is now Spec 2. Note that the `no expiry/reminders, recipes, shopping list, offline mode` bans still hold.

- [ ] **Step 2: Delete the superseded delete method**

Remove `ShelfRepository.delete(householdId, locationId, shelfId)` and `LocationRepository.delete(householdId, locationId)` now that every call site uses `deleteWithStrategy`. Remove the overrides from the test fakes.

- [ ] **Step 3: Run the full gate**

Run: `./gradlew testDebugUnitTest ktlintCheck detekt lint`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md ROADMAP.md app/src/main/java/dev/scuttle/inventory app/src/test
git commit -m "docs: update CLAUDE.md for the new nav and the roles decision

The navigation section still described the 5-tab bar with Households and a
gear-icon Settings, and the scope guardrails still banned roles/permissions —
both now contradicted by shipped behaviour and by the 2026-07-13 decision.

Also drops the superseded strategy-less delete methods."
```

---

## Self-review

**Spec coverage.** Nav rework (T7) · edit mode on all three lists (T4, T5, T6) · shelf tabs⇄list toggle (T4) · ordering rule (T2) · collapsible groups + local household order (T8) · household edit page with danger zone (T6) · delete-strategy dialog + batch undo (T3, T4, T5) · `canRestructure` seam — **see gap below** · stars on products — **see gap below** · CLAUDE.md rewrite (T10).

**Two gaps I am flagging rather than hiding:**

1. **The `canRestructure` seam is not surfaced on the client.** The backend has the policy (backend T2), but nothing here reads it — every user sees the pencil, because every member may restructure today. That is correct behaviour *now*, and it means the client work for roles is deferred wholesale to Spec 2. If you want the seam present on day one, add a `canRestructure: Boolean` to each UI state, hardcoded `true`, and gate the pencil on it. **Cheap; recommended; not in the tasks above.**

2. **Stars on products are backend-only.** Backend T9 ships `is_starred`; no task here renders it. The spec calls for a star marker and a "starred only" filter on the products list. That is a small, self-contained addition to `ProductsPane` + `ProductFilterSortRow` and should be **Task 11** if you want it in this pass.

**Type consistency.** `ShelfDeleteStrategy` / `LocationDeleteStrategy` (T1) are the exact types taken by `confirmDelete` (T4, T5) and `DeleteStrategyDialog.onConfirm` (T3). `DeletePlan(itemCount, productCount, hasOtherTargets)` (T3) is constructed with those three named args in T4 and T5. `orderByPosition(items, position, name)` (T2) is used in T4, T5 and T8.

**Known hazard.** Task 4 removes `deleteMode` / `selectedShelves` / `deleteSelected()` from `ShelvesViewModel`, which `ShelvesViewModelTest` and `LocationDetailScreen` both use today. Both are updated inside that task — but if you run tasks out of order, the build breaks there first.

**Depends on `product_count`.** Task 4's strategy dialog summarises "17 products", which requires a `product_count` on `ShelfResource`. That is **not** in the backend plan — add it (one line in `toArray()`) or the dialog cannot tell the truth.
