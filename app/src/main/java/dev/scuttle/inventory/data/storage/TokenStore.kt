package dev.scuttle.inventory.data.storage

/** Persists the Sanctum bearer token. */
interface TokenStore {
    fun get(): String?

    fun set(token: String)

    fun clear()
}
