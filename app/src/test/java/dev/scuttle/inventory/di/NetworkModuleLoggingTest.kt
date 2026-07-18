package dev.scuttle.inventory.di

import dev.scuttle.inventory.data.api.AuthInterceptor
import dev.scuttle.inventory.data.storage.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG-1 regression guard: distributed builds are debug builds, so the debug logging
 * interceptor ships to testers. It must never write credentials (bearer tokens,
 * passwords, Google id_tokens) to logcat.
 */
class NetworkModuleLoggingTest {
    private class FakeTokenStore : TokenStore {
        override fun get(): String? = "secret-sanctum-token"

        override fun set(token: String) = Unit

        override fun clear() = Unit

        override val authState: StateFlow<Boolean> = MutableStateFlow(true)
    }

    @Test
    fun `okhttp client never logs request bodies`() {
        val client = NetworkModule.provideOkHttpClient(AuthInterceptor(FakeTokenStore()))
        val bodyLevel =
            client.interceptors
                .filterIsInstance<HttpLoggingInterceptor>()
                .any { it.level == HttpLoggingInterceptor.Level.BODY }
        assertFalse("debug logging interceptor must not log bodies", bodyLevel)
    }

    @Test
    fun `logging interceptor redacts authorization and omits bodies`() {
        val lines = mutableListOf<String>()
        val interceptor = NetworkModule.buildLoggingInterceptor { message -> lines += message }
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = """{"token":"response-token"}"""))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val request =
                Request
                    .Builder()
                    .url(server.url("/api/v1/auth/login"))
                    .header("Authorization", "Bearer secret-sanctum-token")
                    .post("""{"email":"a@b.c","password":"hunter2"}""".toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { it.body.string() }
        }
        val log = lines.joinToString("\n")
        assertTrue("request line should still be logged", log.contains("/api/v1/auth/login"))
        assertFalse("bearer token must not be logged", log.contains("secret-sanctum-token"))
        assertFalse("password body must not be logged", log.contains("hunter2"))
        assertFalse("response body must not be logged", log.contains("response-token"))
    }
}
