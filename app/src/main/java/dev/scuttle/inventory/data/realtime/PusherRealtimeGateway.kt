package dev.scuttle.inventory.data.realtime

import android.util.Log
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpChannelAuthorizer
import dev.scuttle.inventory.BuildConfig
import javax.inject.Inject

private const val WSS_PORT = 443

/**
 * Reverb speaks the Pusher protocol, so the stock Pusher client is the
 * transport: wss://{REVERB_HOST}/app/{key}, with private-channel auth against
 * the API's Sanctum-gated /broadcasting/auth endpoint.
 */
class PusherRealtimeGateway
    @Inject
    constructor() : RealtimeGateway {
        private var pusher: Pusher? = null

        override fun connect(
            token: String,
            householdIds: List<Long>,
            onChanged: () -> Unit,
            onConnected: () -> Unit,
            onAuthFailure: () -> Unit,
        ) {
            disconnect()

            val authorizer =
                HttpChannelAuthorizer(BuildConfig.BASE_URL + "broadcasting/auth").apply {
                    setHeaders(
                        mapOf(
                            "Authorization" to "Bearer $token",
                            "Accept" to "application/json",
                        ),
                    )
                }
            val options =
                PusherOptions()
                    .setHost(BuildConfig.REVERB_HOST)
                    .setWssPort(WSS_PORT)
                    .setUseTLS(true)
                    .setChannelAuthorizer(authorizer)

            pusher =
                Pusher(BuildConfig.REVERB_APP_KEY, options).also { client ->
                    client.connect(
                        object : ConnectionEventListener {
                            override fun onConnectionStateChange(change: ConnectionStateChange) {
                                // Covers the client's INTERNAL auto-reconnects
                                // too, not just the first handshake — any events
                                // broadcast while the socket was down are gone,
                                // so every arrival at CONNECTED must trigger a
                                // re-fetch upstream.
                                if (change.currentState == ConnectionState.CONNECTED) onConnected()
                            }

                            override fun onError(
                                message: String?,
                                code: String?,
                                e: Exception?,
                            ) = Unit
                        },
                        ConnectionState.ALL,
                    )
                    val listener =
                        object : PrivateChannelEventListener {
                            override fun onEvent(event: PusherEvent) = onChanged()

                            override fun onAuthenticationFailure(
                                message: String?,
                                e: Exception?,
                            ) {
                                // Was a silent Unit: a revoked token or transient
                                // 5xx on /broadcasting/auth killed live updates
                                // for the process lifetime with no signal.
                                Log.w("LiveUpdates", "Channel auth failed: " + message, e)
                                onAuthFailure()
                            }

                            override fun onSubscriptionSucceeded(channelName: String?) = Unit
                        }
                    householdIds.forEach { id ->
                        client.subscribePrivate(
                            "private-inventory.household.$id",
                            listener,
                            "household.changed",
                        )
                    }
                }
        }

        override fun disconnect() {
            pusher?.disconnect()
            pusher = null
        }
    }
