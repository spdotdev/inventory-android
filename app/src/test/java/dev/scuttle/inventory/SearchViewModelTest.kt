package dev.scuttle.inventory

import androidx.lifecycle.SavedStateHandle
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.search.SearchViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Mirrors ProductsViewModelTest's minimal fake — no household data needed by these tests. */
    private class FakeHouseholdRepository : HouseholdRepository {
        override fun getCached() = emptyList<HouseholdDto>()

        override suspend fun list() = emptyList<HouseholdDto>()

        override suspend fun create(name: String) =
            HouseholdDto(1, name, "", role = "admin", can_restructure = true, can_manage_members = true)

        override suspend fun join(code: String) =
            HouseholdDto(1, "", code, role = "admin", can_restructure = true, can_manage_members = true)

        override suspend fun leave(householdId: Long) = Unit
    }

    private fun viewModel(repository: SearchRepository) =
        SearchViewModel(repository, TestHierarchy.store(FakeHouseholdRepository()), SavedStateHandle())

    private class FakeSearchRepository : SearchRepository {
        var failNext = false
        val queries = mutableListOf<String>()
        val catalog =
            listOf(
                SearchResultDto(
                    1,
                    "Vanilla ice cream",
                    1,
                    "Garage Chest",
                    "Middle shelf",
                    "Garage Chest › Middle shelf",
                ),
                SearchResultDto(2, "Peas", 4, "Garage Chest", "Top shelf", "Garage Chest › Top shelf"),
            )

        override suspend fun search(
            householdId: Long,
            query: String,
        ): List<SearchResultDto> {
            if (failNext) throw RuntimeException("offline")
            queries += query
            return catalog.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    @Test
    fun query_returns_matches_with_path() =
        runTest {
            val viewModel = viewModel(FakeSearchRepository())
            viewModel.setHousehold(1)

            viewModel.onQueryChange("ice")
            advanceUntilIdle()

            val results = viewModel.state.value.results
            assertEquals(1, results.size)
            assertEquals("Garage Chest › Middle shelf", results.first().path)
        }

    @Test
    fun blank_query_clears_results() =
        runTest {
            val viewModel = viewModel(FakeSearchRepository())
            viewModel.setHousehold(1)
            viewModel.onQueryChange("ice")
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.results
                    .isNotEmpty(),
            )

            viewModel.onQueryChange("")
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.results
                    .isEmpty(),
            )
        }

    @Test
    fun sort_reorders_results_without_refetching() =
        runTest {
            val viewModel = viewModel(FakeSearchRepository())
            viewModel.setHousehold(1)
            // A query that returns both catalog rows (Peas qty 4, Vanilla ice cream qty 1).
            viewModel.onQueryChange("a")
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.results.size)

            viewModel.setSort(SortOrder.QUANTITY_DESC)
            assertEquals(
                listOf("Peas", "Vanilla ice cream"),
                viewModel.state.value.sortedResults
                    .map { it.name },
            )

            viewModel.setSort(SortOrder.NAME_ASC)
            assertEquals(
                listOf("Peas", "Vanilla ice cream"),
                viewModel.state.value.sortedResults
                    .map { it.name },
            )
        }

    @Test
    fun search_failure_surfaces_an_error() =
        runTest {
            val repo = FakeSearchRepository().apply { failNext = true }
            val viewModel = viewModel(repo)
            viewModel.setHousehold(1)

            viewModel.onQueryChange("ice")
            advanceUntilIdle()

            // H3: toUserMessageRes returns an R.string.* id, not the throwable's raw message —
            // a generic RuntimeException always resolves to the caller's fallback resource.
            assertEquals(R.string.error_search_failed, viewModel.state.value.errorRes)
        }

    /**
     * The scan-to-lookup flow (MainActivity's ScanDeliveryAction.NavigateToSearch)
     * calls searchFor() with the scanned code so the user lands on an answered
     * search, not an empty box. Deliberately does NOT call advanceUntilIdle():
     * onQueryChange's 300ms debounce would leave results empty at this point under
     * UnconfinedTestDispatcher (delay() only resolves on an explicit advance) —
     * searchFor has no such delay, so this goes red if searchFor is ever
     * implemented by routing through onQueryChange instead of running immediately.
     */
    @Test
    fun searchFor_runs_immediately_with_no_debounce() =
        runTest {
            val viewModel = viewModel(FakeSearchRepository())
            viewModel.setHousehold(1)

            viewModel.searchFor("ice")

            assertEquals("ice", viewModel.state.value.query)
            assertEquals(1, viewModel.state.value.results.size)
            assertEquals(
                "Vanilla ice cream",
                viewModel.state.value.results
                    .first()
                    .name,
            )
        }

    // GAP6-M1: a remote household.changed ping arrives as hierarchyStore.refresh() only
    // (LiveUpdates.kt) — SearchViewModel must silently re-run the CURRENT query so a
    // visible result list doesn't go stale until a manual pull-to-refresh.
    private class CountingSearchRepository : SearchRepository {
        var calls = 0
        var inFlight: CompletableDeferred<Unit>? = null

        override suspend fun search(
            householdId: Long,
            query: String,
        ): List<SearchResultDto> {
            calls++
            inFlight?.await()
            return emptyList()
        }
    }

    @Test
    fun a_hierarchy_store_refresh_re_runs_the_active_query() =
        runTest {
            val repo = CountingSearchRepository()
            val householdRepo = FakeHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val viewModel = SearchViewModel(repo, store, SavedStateHandle())
            viewModel.setHousehold(1)
            viewModel.searchFor("ice")
            assertEquals(1, repo.calls)

            store.refresh()

            assertEquals(2, repo.calls)
        }

    @Test
    fun a_hierarchy_store_refresh_with_no_active_query_triggers_no_fetch() =
        runTest {
            val repo = CountingSearchRepository()
            val householdRepo = FakeHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val viewModel = SearchViewModel(repo, store, SavedStateHandle())
            viewModel.setHousehold(1)
            assertEquals(0, repo.calls)

            store.refresh()

            assertEquals(0, repo.calls)
        }

    @Test
    fun a_hierarchy_store_refresh_during_an_in_flight_local_search_does_not_clobber_it() =
        runTest {
            val repo = CountingSearchRepository()
            val gate = CompletableDeferred<Unit>()
            repo.inFlight = gate
            val householdRepo = FakeHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val viewModel = SearchViewModel(repo, store, SavedStateHandle())
            viewModel.setHousehold(1)
            viewModel.searchFor("ice")
            // The local search is still suspended awaiting `gate`, so state.loading is true.
            assertTrue(viewModel.state.value.loading)
            assertEquals(1, repo.calls)

            // A remote ping lands mid-flight — must not fire a second, clobbering search.
            store.refresh()
            assertEquals(1, repo.calls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, repo.calls)
        }

    // US-001: doSearch()'s `runCatching { repository.search(...) }` used to swallow
    // CancellationException — when the ViewModel's own viewModelScope is cancelled (screen
    // navigated away from / ViewModel torn down) while a search is in flight, the coroutine
    // resumes into `result.fold(...)`'s onFailure branch instead of propagating cancellation,
    // and stamps `errorRes = error_search_failed` onto a StateFlow nothing will ever read
    // again — exactly the kind of post-cancellation write structured concurrency exists to
    // prevent. Regression: cancel the ViewModel's own scope (via ViewModelStore.clear(), the
    // real mechanism Android uses) while doSearch() is suspended, then assert no failure
    // state was ever written — a fixed doSearch() rethrows CancellationException instead of
    // folding it into `errorRes`, so a cancelled search leaves errorRes untouched (null).
    @Test
    fun cancelling_the_view_model_scope_mid_search_never_writes_an_error() =
        runTest {
            val repo = CountingSearchRepository()
            val gate = CompletableDeferred<Unit>()
            repo.inFlight = gate
            val householdRepo = FakeHouseholdRepository()
            val store = TestHierarchy.store(householdRepo)
            val viewModel = SearchViewModel(repo, store, SavedStateHandle())
            val viewModelStore = androidx.lifecycle.ViewModelStore()
            viewModelStore.put("search", viewModel)
            viewModel.setHousehold(1)

            // Suspends forever inside repository.search (awaiting `gate`) — never succeeds,
            // never throws a "real" failure; the only way this coroutine ever resumes is
            // via cancellation.
            viewModel.searchFor("first")
            assertTrue(viewModel.state.value.loading)
            assertEquals(null, viewModel.state.value.errorRes)

            // The real-world trigger: the screen/ViewModel goes away, cancelling
            // viewModelScope — NOT a second search superseding the first.
            viewModelStore.clear()
            advanceUntilIdle()

            // A cancelled search must never surface as a user-visible error.
            assertEquals(null, viewModel.state.value.errorRes)
        }

    @Test
    fun the_query_survives_process_death_and_reruns_on_first_bind() =
        runTest {
            val saved = SavedStateHandle()
            val repo = FakeSearchRepository()
            val first = SearchViewModel(repo, TestHierarchy.store(FakeHouseholdRepository()), saved)
            first.setHousehold(1)
            first.searchFor("8712345")
            advanceUntilIdle()

            // Process death: a NEW ViewModel is built from the same SavedStateHandle.
            val restored = SearchViewModel(repo, TestHierarchy.store(FakeHouseholdRepository()), saved)
            assertEquals("8712345", restored.state.value.query)
            assertTrue(restored.state.value.scanOriginated)

            // The first household bind re-runs the restored query instead of wiping it.
            restored.setHousehold(1)
            advanceUntilIdle()
            assertEquals("8712345", restored.state.value.query)
            assertTrue(repo.queries.contains("8712345"))
        }
}
