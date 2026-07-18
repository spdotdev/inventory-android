package dev.scuttle.inventory.data.realtime

/**
 * Transport for the live-update pings (Q-3). Abstracted so [LiveUpdates] can be
 * unit-tested without a websocket; the real implementation is Pusher-protocol
 * against Reverb.
 */
interface RealtimeGateway {
    /**
     * Open a connection subscribed to every given household's private channel.
     * [onChanged] fires on any `household.changed` ping; the payload is ignored
     * on purpose — the server is authoritative and the client just re-fetches.
     * [onConnected] fires on every (re)connect, including the transport's own
     * internal auto-reconnects — events fired while disconnected are lost, so
     * the caller refreshes on this signal or data stays stale until a manual
     * pull. [onAuthFailure] fires when private-channel auth is refused; the
     * caller decides whether to retry with a fresh token.
     */
    fun connect(
        token: String,
        householdIds: List<Long>,
        onChanged: () -> Unit,
        onConnected: () -> Unit = {},
        onAuthFailure: () -> Unit = {},
    )

    fun disconnect()
}
