package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.ui.settings.JoinHouseholdViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JoinHouseholdViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHouseholdRepository(
        val joined: HouseholdDto =
            HouseholdDto(2, "Friends", "BBBB", role = "admin", can_restructure = true, can_manage_members = true),
        var failJoin: Boolean = false,
    ) : HouseholdRepository {
        var joinedWith: String? = null

        override fun getCached() = null

        override suspend fun list() = emptyList<HouseholdDto>()

        override suspend fun create(name: String) = joined

        override suspend fun join(code: String): HouseholdDto {
            joinedWith = code
            if (failJoin) throw RuntimeException("Invalid code.")
            return joined
        }

        override suspend fun leave(householdId: Long) {}
    }

    @Test
    fun code_change_updates_state() {
        val vm = JoinHouseholdViewModel(FakeHouseholdRepository(), TestHierarchy.store(FakeHouseholdRepository()))
        vm.onCodeChange("ABC")
        assertEquals("ABC", vm.state.value.code)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.success)
    }

    @Test
    fun scanning_an_invite_qr_shows_and_sends_the_bare_code() =
        runTest {
            // #30: the QR encodes the invite *link*; the API matches the join code exactly,
            // so the link has to be reduced to the code before it reaches the field or the API.
            val repo = FakeHouseholdRepository()
            val vm = JoinHouseholdViewModel(repo, TestHierarchy.store(FakeHouseholdRepository()))

            vm.onCodeScanned("https://inventory.scuttle.dev/join/BBBB-2222")
            assertEquals("BBBB-2222", vm.state.value.code)

            vm.join()
            assertEquals("BBBB-2222", repo.joinedWith)
            assertTrue(vm.state.value.success)
        }

    @Test
    fun a_pasted_invite_link_is_reduced_to_the_code_on_submit() =
        runTest {
            val repo = FakeHouseholdRepository()
            val vm = JoinHouseholdViewModel(repo, TestHierarchy.store(FakeHouseholdRepository()))

            vm.onCodeChange("https://inventory.scuttle.dev/join/BBBB-2222")
            vm.join()

            assertEquals("BBBB-2222", repo.joinedWith)
        }

    @Test
    fun join_with_empty_code_does_nothing() =
        runTest {
            val vm = JoinHouseholdViewModel(FakeHouseholdRepository(), TestHierarchy.store(FakeHouseholdRepository()))
            vm.join()
            assertFalse(vm.state.value.success)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun join_success_sets_success_and_clears_code() =
        runTest {
            val vm = JoinHouseholdViewModel(FakeHouseholdRepository(), TestHierarchy.store(FakeHouseholdRepository()))
            vm.onCodeChange("VALIDCODE")
            vm.join()
            assertTrue(vm.state.value.success)
            assertEquals("", vm.state.value.code)
            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun joining_as_admin_does_not_set_a_member_role_hint() =
        runTest {
            val vm = JoinHouseholdViewModel(FakeHouseholdRepository(), TestHierarchy.store(FakeHouseholdRepository()))
            vm.onCodeChange("VALIDCODE")
            vm.join()
            assertEquals("admin", vm.state.value.joinedRole)
        }

    @Test
    fun joining_as_a_plain_member_records_the_role_for_the_success_hint() =
        runTest {
            val repo =
                FakeHouseholdRepository(
                    joined =
                        HouseholdDto(
                            2,
                            "Friends",
                            "BBBB",
                            role = "member",
                            can_restructure = false,
                            can_manage_members = false,
                        ),
                )
            val vm = JoinHouseholdViewModel(repo, TestHierarchy.store(FakeHouseholdRepository()))
            vm.onCodeChange("VALIDCODE")
            vm.join()
            assertEquals("member", vm.state.value.joinedRole)
        }

    @Test
    fun join_failure_surfaces_error_and_preserves_code() =
        runTest {
            val vm =
                JoinHouseholdViewModel(
                    FakeHouseholdRepository(failJoin = true),
                    TestHierarchy.store(FakeHouseholdRepository()),
                )
            vm.onCodeChange("BADCODE")
            vm.join()
            assertFalse(vm.state.value.success)
            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun code_change_after_error_clears_error() =
        runTest {
            val vm =
                JoinHouseholdViewModel(
                    FakeHouseholdRepository(failJoin = true),
                    TestHierarchy.store(FakeHouseholdRepository()),
                )
            vm.onCodeChange("BAD")
            vm.join()
            assertNotNull(vm.state.value.error)
            vm.onCodeChange("NEW")
            assertNull(vm.state.value.error)
        }

    @Test
    fun successful_join_refreshes_the_hierarchy_store() =
        runTest {
            // X4: joining from Settings must reach the drawer/home hierarchy without a
            // manual pull-to-refresh. This repo's list() returns the joined household.
            val repo =
                object : HouseholdRepository {
                    override fun getCached(): List<HouseholdDto>? = null

                    override suspend fun list() =
                        listOf(
                            HouseholdDto(
                                2,
                                "Friends",
                                "BBBB",
                                role = "admin",
                                can_restructure = true,
                                can_manage_members = true,
                            ),
                        )

                    override suspend fun create(name: String) =
                        HouseholdDto(
                            2,
                            "Friends",
                            "BBBB",
                            role = "admin",
                            can_restructure = true,
                            can_manage_members = true,
                        )

                    override suspend fun join(code: String) =
                        HouseholdDto(
                            2,
                            "Friends",
                            "BBBB",
                            role = "admin",
                            can_restructure = true,
                            can_manage_members = true,
                        )

                    override suspend fun leave(householdId: Long) {}
                }
            val store = TestHierarchy.store(repo)
            val vm = JoinHouseholdViewModel(repo, store)
            vm.onCodeChange("BBBB")

            vm.join()

            val loaded = store.state.first { it.entries.isNotEmpty() }
            assertTrue(loaded.entries.any { it.name == "Friends" })
        }
}
