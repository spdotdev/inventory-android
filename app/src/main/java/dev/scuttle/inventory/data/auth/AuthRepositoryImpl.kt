package dev.scuttle.inventory.data.auth

import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.data.api.AuthApi
import dev.scuttle.inventory.data.dto.GoogleRequest
import dev.scuttle.inventory.data.dto.LoginRequest
import dev.scuttle.inventory.data.dto.RegisterRequest
import dev.scuttle.inventory.data.storage.TokenStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@Serializable
private data class GoogleTokenResponse(
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val okHttpClient: OkHttpClient,
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true }

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

    override suspend fun loginWithGoogleCode(code: String, codeVerifier: String, redirectUri: String) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", BuildConfig.GOOGLE_CLIENT_ID)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: throw Exception("Empty response from Google token endpoint")
            val parsed = json.decodeFromString<GoogleTokenResponse>(raw)
            if (parsed.error != null) {
                throw Exception("Google token error: ${parsed.errorDescription ?: parsed.error}")
            }
            parsed.idToken ?: throw Exception("No id_token in Google response")
        }

        loginWithGoogle(responseBody)
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }
}
