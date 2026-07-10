package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.InviteResponse
import dev.scuttle.inventory.data.invite.InviteRepository
import dev.scuttle.inventory.ui.invite.InviteViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InviteViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeInviteRepository(private val fail: Boolean = false) : InviteRepository {
        override suspend fun invite(householdId: Long): InviteResponse {
            if (fail) throw RuntimeException("offline")
            return InviteResponse(code = "FROST-7K2Q", link = "https://inventory.test/join/FROST-7K2Q")
        }
    }

    @Test
    fun load_populates_code_and_link() =
        runTest {
            val viewModel = InviteViewModel(FakeInviteRepository())

            viewModel.load(householdId = 1)

            assertEquals("FROST-7K2Q", viewModel.state.value.code)
            assertEquals("https://inventory.test/join/FROST-7K2Q", viewModel.state.value.link)
        }

    @Test
    fun load_failure_surfaces_an_error() =
        runTest {
            val viewModel = InviteViewModel(FakeInviteRepository(fail = true))

            viewModel.load(householdId = 1)

            assertEquals("offline", viewModel.state.value.error)
        }
}
