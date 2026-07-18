package dev.scuttle.inventory

import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.error.toUserMessageRes
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/**
 * The shared Throwable -> user-message-resource mapper (network + HTTP status handling). H3:
 * this used to return raw English literals; it now returns R.string.* ids so every caller can
 * localize via `stringResource()`.
 */
class ErrorMappingTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun network_failures_map_to_a_friendly_message() {
        assertEquals(
            R.string.error_network_unreachable,
            UnknownHostException("no dns").toUserMessageRes(R.string.error_generic),
        )
        assertEquals(
            R.string.error_network_unreachable,
            IOException("timeout").toUserMessageRes(R.string.error_generic),
        )
    }

    @Test
    fun known_http_codes_map_by_status() {
        assertEquals(R.string.error_session_expired, http(401).toUserMessageRes(R.string.error_generic))
        assertEquals(R.string.error_forbidden, http(403).toUserMessageRes(R.string.error_generic))
        assertEquals(R.string.error_not_found, http(404).toUserMessageRes(R.string.error_generic))
        assertEquals(R.string.error_sole_owner_transfer_first, http(409).toUserMessageRes(R.string.error_generic))
        assertEquals(R.string.error_invalid_input, http(422).toUserMessageRes(R.string.error_generic))
        assertEquals(R.string.error_too_many_requests, http(429).toUserMessageRes(R.string.error_generic))
        assertEquals(R.string.error_server, http(503).toUserMessageRes(R.string.error_generic))
    }

    @Test
    fun an_unknown_http_code_uses_the_caller_fallback() {
        assertEquals(R.string.error_failed_to_save, http(418).toUserMessageRes(R.string.error_failed_to_save))
    }

    @Test
    fun a_generic_throwable_uses_the_caller_fallback() {
        // H3 scope-down: the old version showed the throwable's own message verbatim here —
        // not representable as a resource id, so this now always resolves to the fallback. See
        // toUserMessageRes's doc comment.
        assertEquals(
            R.string.error_failed_to_save,
            RuntimeException("specific detail").toUserMessageRes(R.string.error_failed_to_save),
        )
        assertEquals(R.string.error_failed_to_save, RuntimeException().toUserMessageRes(R.string.error_failed_to_save))
    }
}
