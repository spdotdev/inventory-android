package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.ui.storage.StorageOverviewViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StorageOverviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeLocationRepository : LocationRepository {
        val items = mutableListOf<LocationDto>()
        var failList = false

        override suspend fun list(householdId: Long): List<LocationDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(householdId: Long, name: String, type: String): LocationDto {
            val dto = LocationDto(id = (items.size + 1).toLong(), name = name, type = type)
            items.add(dto)
            return dto
        }
    }

    @Test
    fun load_populates_locations() = runTest {
        val repo = FakeLocationRepository().apply { items.add(LocationDto(1, "Chest", "freezer")) }
        val viewModel = StorageOverviewViewModel(repo)

        viewModel.load(householdId = 1)

        assertEquals(1, viewModel.state.value.locations.size)
        assertEquals("Chest", viewModel.state.value.locations.first().name)
    }

    @Test
    fun create_adds_a_location_with_the_selected_type() = runTest {
        val viewModel = StorageOverviewViewModel(FakeLocationRepository())
        viewModel.load(householdId = 1)
        viewModel.onNewNameChange("Pantry")
        viewModel.onTypeSelect("pantry")

        viewModel.create()

        val locations = viewModel.state.value.locations
        assertTrue(locations.any { it.name == "Pantry" && it.type == "pantry" })
        assertEquals("", viewModel.state.value.newName)
    }

    @Test
    fun list_failure_surfaces_an_error() = runTest {
        val repo = FakeLocationRepository().apply { failList = true }
        val viewModel = StorageOverviewViewModel(repo)

        viewModel.load(householdId = 1)

        assertEquals("offline", viewModel.state.value.error)
    }
}
