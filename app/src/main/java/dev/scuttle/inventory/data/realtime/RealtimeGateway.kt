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
     */
    fun connect(
        token: String,
        householdIds: List<Long>,
        onChanged: () -> Unit,
    )

    fun disconnect()
}
