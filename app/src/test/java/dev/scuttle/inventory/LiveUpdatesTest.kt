package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.realtime.LiveUpdates
import dev.scuttle.inventory.data.realtime.RealtimeGateway
import dev.scuttle.inventory.data.storage.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveUpdatesTest {
    private class FakeGateway : RealtimeGateway {
        var connectCount = 0
        var disconnectCount = 0
        var lastIds: List<Long> = emptyList()
        var lastOnChanged: (() -> Unit)? = null

        override fun connect(
            token: String,
            householdIds: List<Long>,
            onChanged: () -> Unit,
        ) {
            connectCount++
            lastIds = householdIds
            lastOnChanged = onChanged
        }

        override fun disconnect() {
            disconnectCount++
        }
    }

    private class FakeTokenStore(
        signedIn: Boolean,
    ) : TokenStore {
        private val state = MutableStateFlow(signedIn)
        private var token: String? = if (signedIn) "token" else null

        override fun get(): String? = token

        override fun set(token: String) {
            this.token = token
            state.value = true
        }

        override fun clear() {
            token = null
            state.value = false
        }

        override val authState: StateFlow<Boolean> get() = state
    }

    private class CountingHouseholdRepository : HouseholdRepository {
        var listCalls = 0

        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> {
            listCalls++
            return listOf(HouseholdDto(id = 1, name = "Home", join_code = "AAAA-1111"))
        }

        override suspend fun create(name: String): HouseholdDto = throw NotImplementedError()

        override suspend fun join(code: String): HouseholdDto = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = Unit
    }

    /** Wait (real time — the store refreshes on Dispatchers.IO) for a condition. */
    private suspend fun awaitTrue(condition: () -> Boolean) {
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + 5_000
            while (!condition() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
        }
        assertTrue(condition())
    }

    @Test
    fun connects_in_foreground_and_pings_trigger_a_silent_refresh() =
        runTest {
            val gateway = FakeGateway()
            val repo = CountingHouseholdRepository()
            val store = TestHierarchy.store(repo)
            store.refresh()
            store.state.first { it.entries.isNotEmpty() }

            val tokens = FakeTokenStore(signedIn = true)
            val live = LiveUpdates(gateway, tokens, store, StandardTestDispatcher(testScheduler))
            live.start()
            live.setForeground(true)
            advanceUntilIdle()

            assertEquals(1, gateway.connectCount)
            assertEquals(listOf(1L), gateway.lastIds)

            // A ping re-fetches after the debounce window.
            val before = repo.listCalls
            gateway.lastOnChanged!!.invoke()
            advanceTimeBy(600)
            advanceUntilIdle()
            awaitTrue { repo.listCalls > before }

            // A burst of pings collapses into a single refresh.
            val burstBefore = repo.listCalls
            repeat(5) { gateway.lastOnChanged!!.invoke() }
            advanceTimeBy(600)
            advanceUntilIdle()
            awaitTrue { repo.listCalls == burstBefore + 1 }
        }

    @Test
    fun disconnects_when_backgrounded_and_stays_off_when_signed_out() =
        runTest {
            val gateway = FakeGateway()
            val repo = CountingHouseholdRepository()
            val store = TestHierarchy.store(repo)
            store.refresh()
            store.state.first { it.entries.isNotEmpty() }

            val tokens = FakeTokenStore(signedIn = true)
            val live = LiveUpdates(gateway, tokens, store, StandardTestDispatcher(testScheduler))
            live.start()
            live.setForeground(true)
            advanceUntilIdle()
            assertEquals(1, gateway.connectCount)

            live.setForeground(false)
            advanceUntilIdle()
            assertTrue(gateway.disconnectCount >= 1)
            assertEquals(1, gateway.connectCount)

            // Signing out while foregrounded must not reconnect.
            tokens.clear()
            live.setForeground(true)
            advanceUntilIdle()
            assertEquals(1, gateway.connectCount)
        }
}
