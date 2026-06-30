package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShelvesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeShelfRepository : ShelfRepository {
        val items = mutableListOf<ShelfDto>()
        var failList = false

        override fun getCached(householdId: Long, locationId: Long): List<ShelfDto>? = null

        override suspend fun list(householdId: Long, locationId: Long): List<ShelfDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(householdId: Long, locationId: Long, name: String): ShelfDto {
            val dto = ShelfDto(id = (items.size + 1).toLong(), name = name, position = items.size, location_id = locationId)
            items.add(dto)
            return dto
        }

        override suspend fun delete(householdId: Long, locationId: Long, shelfId: Long) {
            items.removeIf { it.id == shelfId }
        }
    }

    @Test
    fun load_populates_shelves() = runTest {
        val repo = FakeShelfRepository().apply { items.add(ShelfDto(1, "Top", 0, 1L)) }
        val viewModel = ShelvesViewModel(repo)

        viewModel.load(householdId = 1, locationId = 1)

        assertEquals(1, viewModel.state.value.shelves.size)
        assertEquals("Top", viewModel.state.value.shelves.first().name)
    }

    @Test
    fun create_adds_a_shelf_and_clears_the_field() = runTest {
        val viewModel = ShelvesViewModel(FakeShelfRepository())
        viewModel.load(householdId = 1, locationId = 1)
        viewModel.onNewNameChange("Middle")

        viewModel.create()

        assertTrue(viewModel.state.value.shelves.any { it.name == "Middle" })
        assertEquals("", viewModel.state.value.newName)
    }

    @Test
    fun list_failure_surfaces_an_error() = runTest {
        val repo = FakeShelfRepository().apply { failList = true }
        val viewModel = ShelvesViewModel(repo)

        viewModel.load(householdId = 1, locationId = 1)

        assertEquals("offline", viewModel.state.value.error)
    }
}
