package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.ui.households.HouseholdsViewModel
import kotlinx.coroutines.flow.first
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

        override suspend fun leave(householdId: Long) {
            items.removeIf { it.id == householdId }
        }

        override suspend fun updateTheme(
            householdId: Long,
            color: String?,
            icon: String?,
        ): HouseholdDto {
            val index = items.indexOfFirst { it.id == householdId }
            items[index] = items[index].copy(color = color, icon = icon)
            return items[index]
        }
    }

    @Test
    fun loads_households_on_init() =
        runTest {
            val viewModel =
                HouseholdsViewModel(FakeHouseholdRepository(), TestHierarchy.store(FakeHouseholdRepository()))

            val state = viewModel.state.value
            assertEquals(1, state.households.size)
            assertEquals("Garage", state.households.first().name)
            assertFalse(state.loading)
        }

    @Test
    fun create_adds_a_household_and_clears_the_field() =
        runTest {
            val viewModel =
                HouseholdsViewModel(FakeHouseholdRepository(), TestHierarchy.store(FakeHouseholdRepository()))
            viewModel.onNewNameChange("Pantry")

            viewModel.create()

            val state = viewModel.state.value
            assertEquals(2, state.households.size)
            assertTrue(state.households.any { it.name == "Pantry" })
            assertEquals("", state.newName)
        }

    @Test
    fun create_refreshes_the_hierarchy_store() =
        runTest {
            // X4: a newly-created household must reach the drawer/home hierarchy without
            // a manual pull-to-refresh.
            val repo = FakeHouseholdRepository()
            val store = TestHierarchy.store(repo)
            val viewModel = HouseholdsViewModel(repo, store)
            viewModel.onNewNameChange("Pantry")

            viewModel.create()

            val loaded = store.state.first { s -> s.entries.any { it.name == "Pantry" } }
            assertTrue(loaded.entries.any { it.name == "Pantry" })
        }

    @Test
    fun update_theme_updates_the_list_and_the_hierarchy() =
        runTest {
            val repo = FakeHouseholdRepository()
            val store = TestHierarchy.store(repo)
            val viewModel = HouseholdsViewModel(repo, store)

            viewModel.updateTheme(householdId = 1, color = "teal", icon = "cottage")

            val household = viewModel.state.value.households.first()
            assertEquals("teal", household.color)
            assertEquals("cottage", household.icon)
            // The drawer reads HierarchyStore — the theme must reach it too.
            val entry = store.state.first { s -> s.entries.isNotEmpty() }.entries.first()
            assertEquals("teal", entry.color)
            assertEquals("cottage", entry.icon)

            // Clearing goes back to null (derived default).
            viewModel.updateTheme(householdId = 1, color = null, icon = null)
            assertEquals(null, viewModel.state.value.households.first().color)
        }

    @Test
    fun list_failure_surfaces_an_error() =
        runTest {
            val repo = FakeHouseholdRepository().apply { failList = true }
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))

            assertEquals("offline", viewModel.state.value.error)
        }

    @Test
    fun leave_removes_household_from_list() =
        runTest {
            val repo =
                FakeHouseholdRepository().apply {
                    items.add(HouseholdDto(id = 2, name = "Office", join_code = "BBBB-2222"))
                }
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))
            assertEquals(2, viewModel.state.value.households.size)

            viewModel.leave(householdId = 1)

            assertEquals(1, viewModel.state.value.households.size)
            assertEquals("Office", viewModel.state.value.households.first().name)
        }
}
