package dev.scuttle.inventory

import android.net.Uri
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.LocationDeletion
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class DrawerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository(
        val households: List<HouseholdDto>,
    ) : HouseholdRepository {
        override fun getCached() = households

        override suspend fun list() = households

        override suspend fun create(name: String) = households.first()

        override suspend fun join(code: String) = households.first()

        override suspend fun leave(householdId: Long) {}
    }

    private class FakeLocationRepository(
        private val initial: Map<Long, List<LocationDto>> = emptyMap(),
    ) : LocationRepository {
        // getCached() returns a FROZEN snapshot of what was true at construction —
        // exactly like HierarchyStore's own `entries`, which is only rebuilt by an
        // explicit store.refresh()/loadFromCache(), never by a location-level
        // network call made elsewhere. `live` is the separate, mutable "what the
        // server actually has right now" that list()/deleteWithStrategy() read
        // and write — kept apart from `initial` on purpose, so a test can move it
        // out from under the frozen snapshot to prove requestDelete() re-fetches
        // instead of trusting a stale cached copy (see
        // requesting_delete_fetches_a_fresh_location_list_so_stale_cached_shelf_count_cannot_skip_the_strategy_prompt).
        private val cached: Map<Long, List<LocationDto>> = initial
        val live = initial.mapValues { it.value.toMutableList() }.toMutableMap()

        // Recorded for assertions below — the real LocationRepository interface bundles
        // batchId/strategy/targetLocationId into one LocationDeletion (data/hierarchy/DeleteStrategy.kt),
        // so each call's deletion is captured whole and unpacked into these lists.
        val batchIdsUsed = mutableListOf<String>()
        val strategiesUsed = mutableListOf<LocationDeleteStrategy?>()
        val targetIdsUsed = mutableListOf<Long?>()
        val deletedLocationIds = mutableListOf<Long>()

        // When set, deleteWithStrategy throws for this one id instead of deleting it —
        // simulates a server-side failure (e.g. an invalid move target) mid-request.
        var failDeleteWithStrategyId: Long? = null

        override fun getCached(householdId: Long) = cached[householdId]

        override suspend fun list(householdId: Long) = live[householdId].orEmpty()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ) = LocationDto(99, name, type)

        override suspend fun deleteWithStrategy(
            householdId: Long,
            locationId: Long,
            deletion: LocationDeletion,
        ) {
            if (locationId == failDeleteWithStrategyId) throw IOException("delete failed")
            batchIdsUsed += deletion.batchId
            strategiesUsed += deletion.strategy
            targetIdsUsed += deletion.targetLocationId
            deletedLocationIds += locationId
            live[householdId]?.removeIf { it.id == locationId }
        }
    }

    private class FakeRestoreRepository : RestoreRepository {
        var lastHouseholdId: Long? = null
        var lastBatchId: String? = null
        var restoreCalls = 0
        var fail = false

        override suspend fun restore(
            householdId: Long,
            batchId: String,
        ): Int {
            if (fail) throw IOException("restore failed")
            lastHouseholdId = householdId
            lastBatchId = batchId
            restoreCalls++
            return 1
        }
    }

    private class FakeShelfRepository(
        val byLocation: Map<Long, List<ShelfDto>> = emptyMap(),
    ) : ShelfRepository {
        override fun getCached(
            householdId: Long,
            locationId: Long,
        ) = byLocation[locationId]

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ) = byLocation[locationId].orEmpty()

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ) = ShelfDto(99, name, 0, locationId)
    }

    private class FakeProductRepository(
        val byShelf: Map<Long, List<ProductDto>> = emptyMap(),
    ) : ProductRepository {
        override fun getCached(
            householdId: Long,
            shelfId: Long,
        ) = byShelf[shelfId]

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ) = byShelf[shelfId].orEmpty()

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            name: String,
            quantity: Int,
            code: String?,
        ) = ProductDto(99, name, quantity, shelfId)

        override suspend fun update(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            edit: ProductEdit,
        ) = ProductDto(productId, edit.name, 0, shelfId)

        override suspend fun add(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = byShelf[shelfId]!!.first { it.id == productId }

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = byShelf[shelfId]!!.first {
            it.id == productId
        }

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            targetShelfId: Long,
        ) = byShelf[shelfId]!!.first {
            it.id == productId
        }

        override suspend fun delete(
            householdId: Long,
            shelfId: Long,
            productId: Long,
        ) = "batch"

        override suspend fun uploadImage(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            imageUri: Uri,
            mimeType: String,
        ) = byShelf[shelfId]!!.first {
            it.id == productId
        }
    }

    private fun makeStore(
        households: List<HouseholdDto>,
        locationsByHousehold: Map<Long, List<LocationDto>> = emptyMap(),
        shelvesByLocation: Map<Long, List<ShelfDto>> = emptyMap(),
        productsByShelf: Map<Long, List<ProductDto>> = emptyMap(),
        locationRepo: LocationRepository? = null,
    ): Pair<HierarchyStore, LocationRepository> {
        val locRepo = locationRepo ?: FakeLocationRepository(locationsByHousehold)
        val store =
            HierarchyStore(
                FakeHouseholdRepository(households),
                locRepo,
                FakeShelfRepository(shelvesByLocation),
                FakeProductRepository(productsByShelf),
                // A fresh, unconfined test dispatcher — not the production
                // Dispatchers.IO the 4-arg constructor falls back to (Minor 5,
                // Task 5/5b review). See TestHierarchy.store()'s own doc comment.
                UnconfinedTestDispatcher(),
            )
        store.loadFromCache()
        return store to locRepo
    }

    private fun viewModel(
        store: HierarchyStore,
        locationRepo: LocationRepository,
        restoreRepository: RestoreRepository = FakeRestoreRepository(),
    ): DrawerViewModel = DrawerViewModel(store, locationRepo, restoreRepository)

    @Test
    fun refresh_populates_entries_with_locations() =
        runTest {
            val (store, locRepo) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                )
            val vm = viewModel(store, locRepo)

            assertEquals(1, vm.state.value.entries.size)
            assertEquals(
                "Home",
                vm.state.value.entries
                    .first()
                    .name,
            )
            assertEquals(
                1,
                vm.state.value.entries
                    .first()
                    .locations.size,
            )
        }

    @Test
    fun refresh_counts_missing_mandatory_items() =
        runTest {
            val (store, locRepo) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationsByHousehold = mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))),
                    shelvesByLocation = mapOf(10L to listOf(ShelfDto(100, "Top", 0, 10))),
                    productsByShelf =
                        mapOf(
                            100L to
                                listOf(
                                    ProductDto(1, "Milk", 0, 100, is_mandatory = true),
                                    ProductDto(2, "Butter", 1, 100, is_mandatory = true),
                                ),
                        ),
                )
            val vm = viewModel(store, locRepo)

            assertEquals(1, vm.state.value.missingItemCount)
        }

    @Test
    fun report_location_warning_updates_map() =
        runTest {
            val (store, locRepo) = makeStore(households = emptyList())
            val vm = viewModel(store, locRepo)

            vm.reportLocationWarning(locationId = 10, hasWarning = true)
            assertTrue(vm.state.value.locationWarnings[10] == true)

            vm.reportLocationWarning(locationId = 10, hasWarning = false)
            assertTrue(vm.state.value.locationWarnings[10] == false)
        }

    private class ThrowingHouseholdRepository : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = throw IOException("network down")

        override suspend fun create(name: String) = throw NotImplementedError()

        override suspend fun join(code: String) = throw NotImplementedError()

        override suspend fun leave(householdId: Long) {}
    }

    @Test
    fun refresh_failure_surfaces_error_and_clears_loading() =
        runTest {
            // W3: a failed load must reach DrawerUiState.error so AllStorages can show
            // a retry instead of the "No storages yet" empty state.
            val store =
                HierarchyStore(
                    ThrowingHouseholdRepository(),
                    FakeLocationRepository(),
                    FakeShelfRepository(),
                    FakeProductRepository(),
                    UnconfinedTestDispatcher(),
                )
            val vm = viewModel(store, FakeLocationRepository())

            store.refresh(userInitiated = true)

            val failed = vm.state.first { it.error != null }
            assertNotNull(failed.error)
            assertFalse(failed.loading)
            assertFalse(failed.refreshing)
            assertTrue(failed.entries.isEmpty())
        }

    @Test
    fun request_delete_does_not_delete_anything_until_confirmed() =
        runTest {
            // The confirm dialog can never be bypassed. Requesting must NOT mutate
            // the repository — only an explicit confirmDelete() may do that.
            val repo =
                FakeLocationRepository(
                    mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge", shelf_count = 0, product_count = 3))),
                )
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)

            vm.requestDelete(householdId = 1, locationId = 10)

            assertTrue(repo.batchIdsUsed.isEmpty())
            assertNotNull(vm.state.value.pendingDelete)
            assertEquals(
                3,
                vm.state.value.pendingDelete
                    ?.productCount,
            )
        }

    @Test
    fun deleting_a_location_whose_shelves_are_empty_still_needs_a_strategy() =
        runTest {
            // The trap: the server asks about a location's SHELVES, not its
            // products. A location with 3 empty shelves has product_count 0 but
            // shelf_count 3 — contentCount must be built from shelf_count, or this
            // delete goes out with no strategy and 422s (exactly the bug this task
            // fixes, one screen over from where Task 5 already fixed it).
            val repo =
                FakeLocationRepository(
                    mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge", shelf_count = 3, product_count = 0))),
                )
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)

            vm.requestDelete(householdId = 1, locationId = 10)

            val plan = vm.state.value.pendingDelete
            assertNotNull(plan)
            assertEquals(3, plan?.contentCount)
            assertEquals(0, plan?.productCount)
            assertTrue(plan?.needsStrategy == true)
        }

    @Test
    fun requesting_delete_fetches_a_fresh_location_list_so_stale_cached_shelf_count_cannot_skip_the_strategy_prompt() =
        runTest {
            // Home renders from HierarchyStore's cached `entries`, which only
            // change on the next store.refresh() — a shelf added elsewhere
            // (LocationDetailScreen's own add-shelf sheet) never updates them on
            // its own. requestDelete() must go straight to
            // LocationRepository.list() rather than trusting entries, or this
            // scenario reintroduces the 422.
            val repo =
                FakeLocationRepository(
                    mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge", shelf_count = 0, product_count = 0))),
                )
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)
            // state.entries is now a snapshot with shelf_count = 0 — the VM never
            // reloads it as part of requestDelete().
            assertEquals(
                0,
                vm.state.value.entries
                    .first()
                    .locations
                    .first()
                    .shelf_count,
            )

            // The server-side shelf count changed through a path this screen never
            // observes; only the repository's own live (network-backed) data
            // reflects it — the frozen `getCached()` snapshot backing
            // state.entries does not, exactly like the real
            // LocationRepositoryImpl/HierarchyStore split.
            val staleEntry = repo.live.getValue(1L)[0]
            repo.live.getValue(1L)[0] = staleEntry.copy(shelf_count = 1)

            vm.requestDelete(householdId = 1, locationId = 10)

            val plan = vm.state.value.pendingDelete
            assertNotNull(plan)
            assertEquals(1, plan?.contentCount)
            assertTrue(plan?.needsStrategy == true)
        }

    @Test
    fun requesting_delete_offers_only_other_live_locations_in_the_same_household_as_move_targets() =
        runTest {
            // A move target has to live in the SAME household as what's being
            // deleted (HierarchyDeleter::deleteLocation only ever looks up
            // $household->locations()). Home shows every household on one
            // screen, so this is the one new way this trap could reappear:
            // offering another household's location as a place to move
            // contents into would 422 (or worse, silently target the wrong
            // household's location of the same id).
            val repo =
                FakeLocationRepository(
                    mapOf(
                        1L to
                            listOf(
                                LocationDto(10, "Fridge", "fridge"),
                                LocationDto(11, "Freezer", "freezer"),
                            ),
                        2L to listOf(LocationDto(20, "Pantry", "pantry")),
                    ),
                )
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                            HouseholdDto(
                                2,
                                "Cabin",
                                "BBBB",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)

            vm.requestDelete(householdId = 1, locationId = 10)

            assertEquals(
                listOf(11L),
                vm.state.value.moveTargets
                    .map { it.id },
            )
        }

    @Test
    fun deleting_the_only_location_in_a_household_offers_no_move_target() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)

            vm.requestDelete(householdId = 1, locationId = 10)

            assertTrue(
                vm.state.value.moveTargets
                    .isEmpty(),
            )
        }

    @Test
    fun requesting_delete_for_a_location_that_no_longer_exists_does_nothing() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)

            vm.requestDelete(householdId = 1, locationId = 999)

            assertNull(vm.state.value.pendingDelete)
        }

    @Test
    fun confirming_delete_sends_a_strategy_and_a_client_minted_batch_id_via_delete_with_strategy() =
        runTest {
            // THE regression this task fixes: before it, Home's swipe-to-delete
            // called a bodyless LocationRepository.delete() — no strategy, no
            // deletion_batch_id. That method (and its ShelfRepository twin) has
            // since been deleted outright (final review, Blocker 3): it had zero
            // production callers and the server unconditionally 422s a bodyless
            // delete, so it was a landmine for the next caller to wire one up.
            // FakeLocationRepository below only implements deleteWithStrategy —
            // any regression back to a bare delete() call now fails to compile.
            val repo =
                FakeLocationRepository(
                    mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge", shelf_count = 2, product_count = 5))),
                )
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)
            vm.requestDelete(householdId = 1, locationId = 10)

            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            assertEquals(listOf(10L), repo.deletedLocationIds)
            assertEquals(1, repo.batchIdsUsed.size)
            // Client-minted and a real uuid — the server's `'deletion_batch_id' =>
            // ['required', 'uuid']` rule rejects anything else.
            UUID.fromString(repo.batchIdsUsed.first())
            assertEquals(listOf(LocationDeleteStrategy.DELETE_CONTENTS), repo.strategiesUsed)
            assertEquals(listOf<Long?>(null), repo.targetIdsUsed)
            assertEquals(repo.batchIdsUsed.first(), vm.state.value.lastBatchId)
            assertNull(vm.state.value.pendingDelete)
            assertNull(vm.actionError.value)
        }

    @Test
    fun confirming_delete_refreshes_the_hierarchy_store() =
        runTest {
            // Minor 3 (Task 5/5b review): mutating `store.refresh()` in
            // confirmDelete to Unit leaves the suite green — the drawer/home
            // would still render a location the server just deleted. The
            // cached snapshot (loadFromCache, synchronous, reads
            // FakeLocationRepository's FROZEN `cached` map) still has it;
            // only a REAL store.refresh() (async, reads the mutable `live`
            // map deleteWithStrategy() actually writes to) can make it
            // disappear from HierarchyStore's own `entries`.
            val repo =
                FakeLocationRepository(
                    mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge", shelf_count = 0, product_count = 0))),
                )
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)
            vm.requestDelete(householdId = 1, locationId = 10)
            // The cached snapshot still shows "Fridge" — this is what loadFromCache
            // (called once, synchronously, when makeStore() built the store) produced.
            assertTrue(
                store.state.value.entries
                    .first()
                    .locations
                    .any { it.name == "Fridge" },
            )

            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            val refreshed =
                store.state.first {
                    it.entries
                        .first()
                        .locations
                        .none { l -> l.name == "Fridge" }
                }
            assertTrue(
                refreshed.entries
                    .first()
                    .locations
                    .isEmpty(),
            )
        }

    @Test
    fun confirming_a_move_delete_sends_the_target_location_id() =
        runTest {
            val repo =
                FakeLocationRepository(
                    mapOf(
                        1L to
                            listOf(
                                LocationDto(10, "Fridge", "fridge", shelf_count = 1),
                                LocationDto(11, "Pantry", "pantry"),
                            ),
                    ),
                )
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)
            vm.requestDelete(householdId = 1, locationId = 10)

            vm.confirmDelete(LocationDeleteStrategy.MOVE_CONTENTS, targetId = 11L)

            assertEquals(listOf(11L), repo.targetIdsUsed)
        }

    @Test
    fun cancel_delete_closes_the_dialog_without_deleting_anything() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)
            vm.requestDelete(householdId = 1, locationId = 10)

            vm.cancelDelete()

            assertNull(vm.state.value.pendingDelete)
            assertTrue(repo.batchIdsUsed.isEmpty())

            // Confirming after a cancel must be a no-op — there is no pending
            // location left to delete.
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            assertTrue(repo.batchIdsUsed.isEmpty())
        }

    @Test
    fun a_failed_delete_with_strategy_surfaces_an_action_error_and_closes_the_dialog() =
        runTest {
            // W10: a failed delete must not be swallowed — it surfaces as a
            // one-shot actionError the screen shows as a snackbar, same as the
            // legacy bodyless-delete failure path did.
            val repo =
                FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge")))).apply {
                    failDeleteWithStrategyId = 10L
                }
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo)
            vm.requestDelete(householdId = 1, locationId = 10)

            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            val message = vm.actionError.first { it != null }
            assertNotNull(message)
            assertNull(vm.state.value.pendingDelete)
            assertNull(vm.state.value.lastBatchId)
        }

    @Test
    fun undo_delete_restores_the_batch_and_clears_it() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))
            val restore = FakeRestoreRepository()
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo, restore)
            vm.requestDelete(householdId = 1, locationId = 10)
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            val batchId = vm.state.value.lastBatchId
            assertNotNull(batchId)

            vm.undoDelete()

            assertEquals(1L, restore.lastHouseholdId)
            assertEquals(batchId, restore.lastBatchId)
            assertNull(vm.state.value.lastBatchId)
            // ALSO FIX (final review): a successful undo sets undoResult so the
            // screen can show the "Restored." snackbar (delete_undone).
            assertEquals(UndoOutcome.SUCCESS, vm.state.value.undoResult)
        }

    @Test
    fun a_failed_undo_sets_the_failure_result_instead_of_a_generic_action_error() =
        runTest {
            // ALSO FIX (final review): a 409 from the restore endpoint (already
            // restored elsewhere, or past the undo window) used to fall through to
            // the generic actionError/"Failed to undo delete." — undoResult now
            // carries the specific outcome instead, and actionError stays unset
            // so the screen doesn't show BOTH messages.
            val repo = FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))
            val restore = FakeRestoreRepository()
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo, restore)
            vm.requestDelete(householdId = 1, locationId = 10)
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            restore.fail = true

            vm.undoDelete()

            assertEquals(UndoOutcome.FAILURE, vm.state.value.undoResult)
            assertNull(vm.actionError.value)
            // The batch id survives a failed undo — nothing was actually restored.
            assertNotNull(vm.state.value.lastBatchId)
        }

    @Test
    fun consume_undo_result_clears_it() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))
            val restore = FakeRestoreRepository()
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo, restore)
            vm.requestDelete(householdId = 1, locationId = 10)
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)
            vm.undoDelete()
            assertNotNull(vm.state.value.undoResult)

            vm.consumeUndoResult()

            assertNull(vm.state.value.undoResult)
        }

    @Test
    fun consume_last_batch_clears_it_without_restoring() =
        runTest {
            val repo = FakeLocationRepository(mapOf(1L to listOf(LocationDto(10, "Fridge", "fridge"))))
            val restore = FakeRestoreRepository()
            val (store, _) =
                makeStore(
                    households =
                        listOf(
                            HouseholdDto(
                                1,
                                "Home",
                                "AAAA",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        ),
                    locationRepo = repo,
                )
            val vm = viewModel(store, repo, restore)
            vm.requestDelete(householdId = 1, locationId = 10)
            vm.confirmDelete(LocationDeleteStrategy.DELETE_CONTENTS, targetId = null)

            vm.consumeLastBatch()

            assertNull(vm.state.value.lastBatchId)
            assertEquals(0, restore.restoreCalls)
        }
}
