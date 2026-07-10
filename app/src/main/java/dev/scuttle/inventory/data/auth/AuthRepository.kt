package dev.scuttle.inventory.data.auth

interface AuthRepository {
    fun isAuthenticated(): Boolean

    /** Reactive session state: flips to false when the token is cleared (logout or a 401). */
    val sessionActive: kotlinx.coroutines.flow.StateFlow<Boolean>

    suspend fun register(
        name: String,
        email: String,
        password: String,
    )

    suspend fun login(
        email: String,
        password: String,
    )

    suspend fun loginWithGoogle(idToken: String)

    suspend fun loginWithGoogleCode(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    )

    suspend fun forgotPassword(email: String)

    suspend fun logout()
}
