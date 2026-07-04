package dev.scuttle.inventory

import dev.scuttle.inventory.data.api.AuthInterceptor
import dev.scuttle.inventory.data.storage.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pure-JVM coverage of the reactive-logout mechanism (auth/token is a critical
 * path per CLAUDE.md): a 401 must clear the stored token (which flips authState →
 * the app returns to login); a non-401 must not; and the Bearer header must be
 * attached only when a token exists. Uses a fake [Interceptor.Chain] so no
 * emulator / MockWebServer is needed.
 */
class AuthInterceptorTest {

    private class FakeTokenStore(initial: String?) : TokenStore {
        private var token = initial
        private val _authState = MutableStateFlow(initial != null)
        override fun get(): String? = token
        override fun set(token: String) { this.token = token; _authState.value = true }
        override fun clear() { token = null; _authState.value = false }
        override val authState = _authState
    }

    private class FakeChain(
        private val request: Request,
        private val responseCode: Int,
        val onProceed: (Request) -> Unit = {},
    ) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response {
            onProceed(request)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("")
                .body("".toResponseBody(null))
                .build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = throw NotImplementedError()
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private val request = Request.Builder().url("http://localhost/api/v1/households").build()

    @Test
    fun clears_the_token_on_a_401() {
        val store = FakeTokenStore("valid-token")
        AuthInterceptor(store).intercept(FakeChain(request, responseCode = 401))
        assertNull(store.get())
        assertEquals(false, store.authState.value)
    }

    @Test
    fun keeps_the_token_on_a_200() {
        val store = FakeTokenStore("valid-token")
        AuthInterceptor(store).intercept(FakeChain(request, responseCode = 200))
        assertEquals("valid-token", store.get())
        assertEquals(true, store.authState.value)
    }

    @Test
    fun attaches_bearer_header_only_when_a_token_exists() {
        var withToken: Request? = null
        AuthInterceptor(FakeTokenStore("abc123"))
            .intercept(FakeChain(request, 200) { withToken = it })
        assertEquals("Bearer abc123", withToken?.header("Authorization"))

        var withoutToken: Request? = null
        AuthInterceptor(FakeTokenStore(null))
            .intercept(FakeChain(request, 200) { withoutToken = it })
        assertNull(withoutToken?.header("Authorization"))
    }
}
