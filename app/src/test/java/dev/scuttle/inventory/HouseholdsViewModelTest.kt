package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.ui.households.HouseholdsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HouseholdsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository : HouseholdRepository {
        val items = mutableListOf(HouseholdDto(id = 1, name = "Garage", join_code = "AAAA-1111"))
        var failList = false

        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList()
        }

        override suspend fun create(name: String): HouseholdDto {
            val dto = HouseholdDto(id = (items.size + 1).toLong(), name = name, join_code = "NEW-0000")
            items.add(dto)
            return dto
        }

        override suspend fun join(code: String): HouseholdDto = items.first()

        override suspend fun leave(householdId: Long) { items.removeIf { it.id == householdId } }
    }

    @Test
    fun loads_households_on_init() = runTest {
        val viewModel = HouseholdsViewModel(FakeHouseholdRepository())

        val state = viewModel.state.value
        assertEquals(1, state.households.size)
        assertEquals("Garage", state.households.first().name)
        assertFalse(state.loading)
    }

    @Test
    fun create_adds_a_household_and_clears_the_field() = runTest {
        val viewModel = HouseholdsViewModel(FakeHouseholdRepository())
        viewModel.onNewNameChange("Pantry")

        viewModel.create()

        val state = viewModel.state.value
        assertEquals(2, state.households.size)
        assertTrue(state.households.any { it.name == "Pantry" })
        assertEquals("", state.newName)
    }

    @Test
    fun list_failure_surfaces_an_error() = runTest {
        val repo = FakeHouseholdRepository().apply { failList = true }
        val viewModel = HouseholdsViewModel(repo)

        assertEquals("offline", viewModel.state.value.error)
    }
}
