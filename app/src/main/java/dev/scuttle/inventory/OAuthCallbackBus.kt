package dev.scuttle.inventory

import kotlinx.coroutines.flow.MutableSharedFlow

object OAuthCallbackBus {
    val codeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    var pendingCodeVerifier: String? = null
}
