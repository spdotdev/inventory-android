package dev.scuttle.inventory.data.auth

import dev.scuttle.inventory.data.api.AuthApi
import dev.scuttle.inventory.data.dto.GoogleRequest
import dev.scuttle.inventory.data.dto.LoginRequest
import dev.scuttle.inventory.data.dto.RegisterRequest
import dev.scuttle.inventory.data.storage.TokenStore
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override fun isAuthenticated(): Boolean = tokenStore.get() != null

    override suspend fun register(name: String, email: String, password: String) {
        val response = api.register(RegisterRequest(name = name, email = email, password = password))
        tokenStore.set(response.token)
    }

    override suspend fun login(email: String, password: String) {
        val response = api.login(LoginRequest(email = email, password = password))
        tokenStore.set(response.token)
    }

    override suspend fun loginWithGoogle(idToken: String) {
        val response = api.google(GoogleRequest(id_token = idToken))
        tokenStore.set(response.token)
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }
}
