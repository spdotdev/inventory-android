package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.ui.search.SearchViewModel
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
        val catalog = listOf(
            SearchResultDto(1, "Vanilla ice cream", 1, "Garage Chest", "Middle shelf", "Garage Chest › Middle shelf"),
            SearchResultDto(2, "Peas", 4, "Garage Chest", "Top shelf", "Garage Chest › Top shelf"),
        )

        override suspend fun search(householdId: Long, query: String): List<SearchResultDto> {
            if (failNext) throw RuntimeException("offline")
            return catalog.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    @Test
    fun query_returns_matches_with_path() = runTest {
        val viewModel = SearchViewModel(FakeSearchRepository())
        viewModel.setHousehold(1)

        viewModel.onQueryChange("ice")

        val results = viewModel.state.value.results
        assertEquals(1, results.size)
        assertEquals("Garage Chest › Middle shelf", results.first().path)
    }

    @Test
    fun blank_query_clears_results() = runTest {
        val viewModel = SearchViewModel(FakeSearchRepository())
        viewModel.setHousehold(1)
        viewModel.onQueryChange("ice")
        assertTrue(viewModel.state.value.results.isNotEmpty())

        viewModel.onQueryChange("")

        assertTrue(viewModel.state.value.results.isEmpty())
    }

    @Test
    fun search_failure_surfaces_an_error() = runTest {
        val repo = FakeSearchRepository().apply { failNext = true }
        val viewModel = SearchViewModel(repo)
        viewModel.setHousehold(1)

        viewModel.onQueryChange("ice")

        assertEquals("offline", viewModel.state.value.error)
    }
}
