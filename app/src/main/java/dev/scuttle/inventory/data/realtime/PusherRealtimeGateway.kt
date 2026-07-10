package dev.scuttle.inventory.data.realtime

import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
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
                    client.connect()
                    val listener =
                        object : PrivateChannelEventListener {
                            override fun onEvent(event: PusherEvent) = onChanged()

                            override fun onAuthenticationFailure(
                                message: String?,
                                e: Exception?,
                            ) = Unit

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
