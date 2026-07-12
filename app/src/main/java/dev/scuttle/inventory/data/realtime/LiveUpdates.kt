package dev.scuttle.inventory.data.realtime

import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.storage.TokenStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val PING_DEBOUNCE_MS = 500L

/**
 * Q-3 live updates: keeps a realtime connection open while the app is in the
 * FOREGROUND and a user is signed in, subscribed to every household they belong
 * to. A `household.changed` ping triggers a silent [HierarchyStore.refresh] —
 * the ping carries no state (server-authoritative), so this is exactly a
 * machine-initiated pull-to-refresh. Foreground-only by design: no background
 * sockets, no battery cost, matching the always-online model.
 */
@Singleton
class LiveUpdates(
    private val gateway: RealtimeGateway,
    private val tokenStore: TokenStore,
    private val hierarchyStore: HierarchyStore,
    dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        gateway: RealtimeGateway,
        tokenStore: TokenStore,
        hierarchyStore: HierarchyStore,
    ) : this(gateway, tokenStore, hierarchyStore, Dispatchers.Default)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val foreground = MutableStateFlow(false)
    private val pings = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    private val started = AtomicBoolean(false)

    /** Wired to the single activity's onStart/onStop. */
    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    /** Idempotent — the activity may be recreated, the singleton is not. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                foreground,
                tokenStore.authState,
                hierarchyStore.state
                    .map { s -> s.entries.map(HouseholdWithLocations::id).sorted() }
                    .distinctUntilChanged(),
            ) { fg, authed, ids -> Triple(fg, authed, ids) }
                .distinctUntilChanged()
                .collect { (fg, authed, ids) ->
                    gateway.disconnect()
                    if (fg && authed && ids.isNotEmpty()) {
                        val token = tokenStore.get() ?: return@collect
                        gateway.connect(token, ids) { pings.tryEmit(Unit) }
                    }
                }
        }
        scope.launch {
            // Debounced: a burst of mutations (e.g. rapid +/+/+ stock taps by
            // another member) collapses into one re-fetch.
            pings.debounce(PING_DEBOUNCE_MS).collect {
                hierarchyStore.refresh()
            }
        }
    }
}
