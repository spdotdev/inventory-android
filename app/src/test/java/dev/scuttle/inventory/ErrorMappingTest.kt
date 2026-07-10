package dev.scuttle.inventory

import dev.scuttle.inventory.data.error.toUserMessage
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/** The shared Throwable -> user-message mapper (network + HTTP status handling). */
class ErrorMappingTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun network_failures_map_to_a_friendly_message() {
        val expected = "Can't reach the server. Check your connection and try again."
        assertEquals(expected, UnknownHostException("no dns").toUserMessage("fallback"))
        assertEquals(expected, IOException("timeout").toUserMessage("fallback"))
    }

    @Test
    fun known_http_codes_map_by_status() {
        assertEquals("Your session has expired. Please sign in again.", http(401).toUserMessage("f"))
        assertEquals("You don't have access to that.", http(403).toUserMessage("f"))
        assertEquals("That could not be found — it may have been removed.", http(404).toUserMessage("f"))
        assertEquals("Please check your input and try again.", http(422).toUserMessage("f"))
        assertEquals("Too many requests. Please wait a moment and try again.", http(429).toUserMessage("f"))
        assertEquals("Server error. Please try again in a moment.", http(503).toUserMessage("f"))
    }

    @Test
    fun an_unknown_http_code_uses_the_caller_fallback() {
        assertEquals("fallback", http(418).toUserMessage("fallback"))
    }

    @Test
    fun a_generic_throwable_uses_its_message_then_the_fallback() {
        assertEquals("specific detail", RuntimeException("specific detail").toUserMessage("fallback"))
        assertEquals("fallback", RuntimeException().toUserMessage("fallback"))
    }
}
