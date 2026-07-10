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
        val joined: HouseholdDto = HouseholdDto(2, "Friends", "BBBB"),
        var failJoin: Boolean = false,
    ) : HouseholdRepository {
        override fun getCached() = null

        override suspend fun list() = emptyList<HouseholdDto>()

        override suspend fun create(name: String) = joined

        override suspend fun join(code: String): HouseholdDto {
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

                    override suspend fun list() = listOf(HouseholdDto(2, "Friends", "BBBB"))

                    override suspend fun create(name: String) = HouseholdDto(2, "Friends", "BBBB")

                    override suspend fun join(code: String) = HouseholdDto(2, "Friends", "BBBB")

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
