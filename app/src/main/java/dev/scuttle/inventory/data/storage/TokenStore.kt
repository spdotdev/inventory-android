package dev.scuttle.inventory.data.storage

import kotlinx.coroutines.flow.StateFlow

/** Persists the Sanctum bearer token. */
interface TokenStore {
    fun get(): String?

    fun set(token: String)

    fun clear()

    /**
     * `true` while a token is stored, `false` once it's cleared. Emits on set/clear
     * so the session can react to a mid-session 401 (the interceptor clears the token
     * off the UI thread) instead of only re-checking on next launch.
     */
    val authState: StateFlow<Boolean>
}
