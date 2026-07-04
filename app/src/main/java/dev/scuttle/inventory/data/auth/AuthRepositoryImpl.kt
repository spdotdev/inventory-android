package dev.scuttle.inventory.data.auth

import dev.scuttle.inventory.data.api.AuthApi
import dev.scuttle.inventory.data.dto.ForgotPasswordRequest
import dev.scuttle.inventory.data.dto.GoogleRequest
import dev.scuttle.inventory.data.dto.LoginRequest
import dev.scuttle.inventory.data.dto.RegisterRequest
import dev.scuttle.inventory.data.storage.TokenStore
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val sessionCleaner: SessionCleaner,
) : AuthRepository {

    override fun isAuthenticated(): Boolean = tokenStore.get() != null

    override val sessionActive = tokenStore.authState

    override suspend fun register(name: String, email: String, password: String) {
        val response = api.register(RegisterRequest(name = name, email = email, password = password))
        onNewSession(response.token)
    }

    override suspend fun login(email: String, password: String) {
        val response = api.login(LoginRequest(email = email, password = password))
        onNewSession(response.token)
    }

    override suspend fun loginWithGoogle(idToken: String) {
        val response = api.google(GoogleRequest(id_token = idToken))
        onNewSession(response.token)
    }

    /**
     * Start every new session from a clean slate. Clearing here (not only in
     * logout) also covers the reactive-401 logout path, where the interceptor
     * clears the token but can't reach the singleton caches — so the stale data
     * is wiped at the next successful sign-in regardless of how the prior session
     * ended.
     */
    private fun onNewSession(token: String) {
        sessionCleaner.clear()
        tokenStore.set(token)
    }

    override suspend fun loginWithGoogleCode(code: String, codeVerifier: String, redirectUri: String) =
        throw UnsupportedOperationException()

    override suspend fun forgotPassword(email: String) {
        api.forgotPassword(ForgotPasswordRequest(email = email))
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
        sessionCleaner.clear()
    }
}
