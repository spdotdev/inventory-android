package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.search.SearchViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSearchRepository : SearchRepository {
        var failNext = false
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
            return catalog.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    @Test
    fun query_returns_matches_with_path() =
        runTest {
            val viewModel = SearchViewModel(FakeSearchRepository())
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
            val viewModel = SearchViewModel(FakeSearchRepository())
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
            val viewModel = SearchViewModel(FakeSearchRepository())
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
            val viewModel = SearchViewModel(repo)
            viewModel.setHousehold(1)

            viewModel.onQueryChange("ice")
            advanceUntilIdle()

            assertEquals("offline", viewModel.state.value.error)
        }
}
