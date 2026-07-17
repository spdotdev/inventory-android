package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.ui.households.HouseholdsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class HouseholdsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository : HouseholdRepository {
        val items =
            mutableListOf(
                HouseholdDto(
                    id = 1,
                    name = "Garage",
                    join_code = "AAAA-1111",
                    role = "admin",
                    can_restructure = true,
                    can_manage_members = true,
                ),
            )
        var failList = false
        var leaveThrows: Throwable? = null

        // Mirrors HouseholdRepositoryImpl's own cache (list() populates it) — a
        // fake that always returns null here would lie about the real contract
        // HierarchyStore.buildFromNetwork()/HouseholdsViewModel's own
        // observeHierarchyStore() depend on (see the "fake that lies" testing
        // lesson in CLAUDE.md).
        private var cache: List<HouseholdDto>? = null

        override fun getCached(): List<HouseholdDto>? = cache

        override suspend fun list(): List<HouseholdDto> {
            if (failList) throw RuntimeException("offline")
            return items.toList().also { cache = it }
        }

        override suspend fun create(name: String): HouseholdDto {
            val dto =
                HouseholdDto(
                    id = (items.size + 1).toLong(),
                    name = name,
                    join_code = "NEW-0000",
                    role = "admin",
                    can_restructure = true,
                    can_manage_members = true,
                )
            items.add(dto)
            return dto
        }

        override suspend fun join(code: String): HouseholdDto = items.first()

        override suspend fun leave(householdId: Long) {
            leaveThrows?.let { throw it }
            items.removeIf { it.id == householdId }
        }

        override suspend fun update(
            householdId: Long,
            name: String?,
            color: String?,
            icon: String?,
        ): HouseholdDto {
            val index = items.indexOfFirst { it.id == householdId }
            items[index] = items[index].copy(name = name ?: items[index].name, color = color, icon = icon)
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

    // HouseholdsScreen itself needs no gating: pencil -> edit mode -> row tap into
    // HouseholdEditScreen stays open to every role, since Leave (not restructure-
    // gated) lives only behind that path. This test just documents that
    // can_restructure flows through HouseholdDto -> HouseholdsUiState.households
    // unmodified, so HouseholdEditScreen (which DOES gate on it) always sees the
    // real value once it reads `state.households.find { it.id == householdId }`.
    @Test
    fun households_carry_can_restructure_through_unmodified() =
        runTest {
            val repo =
                FakeHouseholdRepository().apply {
                    items.add(
                        HouseholdDto(
                            id = 2,
                            name = "Friends' place",
                            join_code = "BBBB-2222",
                            role = "member",
                            can_restructure = false,
                            can_manage_members = false,
                        ),
                    )
                }
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))

            val state = viewModel.state.value

            assertTrue(state.households.first { it.id == 1L }.can_restructure)
            assertFalse(state.households.first { it.id == 2L }.can_restructure)
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

            val household =
                viewModel.state.value.households
                    .first()
            assertEquals("teal", household.color)
            assertEquals("cottage", household.icon)
            // The drawer reads HierarchyStore — the theme must reach it too.
            val entry =
                store.state
                    .first { s -> s.entries.isNotEmpty() }
                    .entries
                    .first()
            assertEquals("teal", entry.color)
            assertEquals("cottage", entry.icon)

            // Clearing goes back to null (derived default).
            viewModel.updateTheme(householdId = 1, color = null, icon = null)
            assertEquals(
                null,
                viewModel.state.value.households
                    .first()
                    .color,
            )
        }

    @Test
    fun renaming_a_household_updates_it_in_place() =
        runTest {
            val repo = FakeHouseholdRepository()
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))

            viewModel.update(1L, name = "House", color = null, icon = null)

            assertEquals(
                "House",
                viewModel.state.value.households
                    .first()
                    .name,
            )
        }

    @Test
    fun renaming_a_household_preserves_its_existing_theme_when_passed_through() =
        runTest {
            // Mirrors HouseholdEditScreen's Save-name action: it passes the
            // household's currently-known color/icon back through (not null),
            // because UpdateHouseholdRequest's color/icon have no default and are
            // ALWAYS encoded on the wire — an explicit null there clears the theme
            // server-side. A rename must never have that side effect.
            val repo = FakeHouseholdRepository().apply { items[0] = items[0].copy(color = "teal", icon = "cottage") }
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))

            viewModel.update(1L, name = "House", color = "teal", icon = "cottage")

            val household =
                viewModel.state.value.households
                    .first()
            assertEquals("House", household.name)
            assertEquals("teal", household.color)
            assertEquals("cottage", household.icon)
        }

    @Test
    fun clearing_the_theme_leaves_the_name_untouched() =
        runTest {
            // Mirrors HouseholdEditScreen's appearance swatches / "Default" action,
            // which calls updateTheme(name = null, ...) — relying on `name`'s
            // default to omit the key so the server's `sometimes|required` rule
            // never sees an explicit null (which would 422) and never touches it.
            val repo = FakeHouseholdRepository()
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))
            viewModel.updateTheme(householdId = 1, color = "teal", icon = "cottage")

            viewModel.updateTheme(householdId = 1, color = null, icon = null)

            val household =
                viewModel.state.value.households
                    .first()
            assertEquals("Garage", household.name)
            assertEquals(null, household.color)
            assertEquals(null, household.icon)
        }

    @Test
    fun edit_mode_starts_off_and_toggles_on_enter_and_exit() =
        runTest {
            val repo = FakeHouseholdRepository()
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))
            assertFalse(viewModel.state.value.editMode)

            viewModel.enterEditMode()
            assertTrue(viewModel.state.value.editMode)

            viewModel.exitEditMode()
            assertFalse(viewModel.state.value.editMode)
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
                    items.add(
                        HouseholdDto(
                            id = 2,
                            name = "Office",
                            join_code = "BBBB-2222",
                            role = "admin",
                            can_restructure = true,
                            can_manage_members = true,
                        ),
                    )
                }
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))
            assertEquals(2, viewModel.state.value.households.size)

            viewModel.leave(householdId = 1)

            assertEquals(1, viewModel.state.value.households.size)
            assertEquals(
                "Office",
                viewModel.state.value.households
                    .first()
                    .name,
            )
        }

    @Test
    fun leave_sets_leftHouseholdId_so_the_edit_screen_can_navigate_back() =
        runTest {
            // HouseholdEditScreen waits for this (rather than navigating back
            // immediately on tap) so the leave() coroutine — running in this
            // ViewModel's own viewModelScope, now shared with HouseholdsScreen across
            // the whole NavHost (MainActivity hoists one instance) — isn't cancelled
            // mid-flight by the navigation it would otherwise trigger before the
            // network call completes. The value is the household's own id, not a
            // plain boolean, precisely BECAUSE the instance is shared: a boolean
            // would stay stuck true after the first leave() this session and
            // auto-navigate back out of the next household-edit visit for any OTHER
            // household — see the field's own doc comment on HouseholdsUiState.
            val repo = FakeHouseholdRepository()
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))
            assertNull(viewModel.state.value.leftHouseholdId)

            viewModel.leave(householdId = 1)

            assertEquals(1L, viewModel.state.value.leftHouseholdId)
        }

    @Test
    fun leaving_as_the_sole_owner_surfaces_a_friendly_409_message() =
        runTest {
            val repo =
                FakeHouseholdRepository().apply {
                    leaveThrows = HttpException(Response.error<Unit>(409, "".toResponseBody(null)))
                }
            val viewModel = HouseholdsViewModel(repo, TestHierarchy.store(repo))

            viewModel.leave(householdId = 1)

            assertEquals(
                "You're the only owner — transfer ownership before leaving this household.",
                viewModel.state.value.error,
            )
        }

    @Test
    fun a_remote_role_change_reaches_this_vm_once_the_hierarchy_store_refreshes_without_a_manual_refresh() =
        runTest {
            // A remote promote/demote/remove arrives as a `household.changed` ping,
            // which LiveUpdates turns into hierarchyStore.refresh() only — never a
            // direct call to this VM. HouseholdEditScreen/MembersScreen read
            // viewerRole/canManageMembers from THIS VM's households, so without
            // observeHierarchyStore() the affected user's pencils/controls stay
            // wrong until a manual pull-to-refresh.
            val repo = FakeHouseholdRepository()
            val store = TestHierarchy.store(repo)
            val viewModel = HouseholdsViewModel(repo, store)
            assertTrue(
                viewModel.state.value.households
                    .first()
                    .can_manage_members,
            )

            // The server-side role changed through a path this VM never observes —
            // simulate it by mutating the repo directly, exactly like a promote/
            // demote call landing on some OTHER device.
            repo.items[0] =
                repo.items[0].copy(role = "member", can_restructure = false, can_manage_members = false)

            // The realtime ping: LiveUpdates only ever calls hierarchyStore.refresh().
            store.refresh()

            val household =
                viewModel.state.value.households
                    .first { it.id == 1L }
            assertEquals("member", household.role)
            assertFalse(household.can_restructure)
            assertFalse(household.can_manage_members)
        }
}
