package dev.scuttle.inventory.data.auth

interface AuthRepository {
    fun isAuthenticated(): Boolean

    suspend fun register(name: String, email: String, password: String)

    suspend fun login(email: String, password: String)

    suspend fun loginWithGoogle(idToken: String)

    suspend fun logout()
}
