package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.DeleteShelfRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteRequestSerializationTest {
    // The app's real Json config. encodeDefaults defaults to false, which is
    // exactly why a defaulted property would be dropped from the body.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun batch_id_is_always_sent() {
        val body =
            json.encodeToString(
                DeleteShelfRequest(
                    strategy = null,
                    target_shelf_id = null,
                    deletion_batch_id = "11111111-1111-4111-8111-111111111111",
                ),
            )

        // The server 422s without it. If a default ever creeps onto this
        // property, encodeDefaults=false silently omits it and every delete
        // starts failing with a validation error nobody can explain.
        assertTrue(body.contains("deletion_batch_id"))
    }

    @Test
    fun a_null_strategy_is_still_sent_explicitly() {
        val body =
            json.encodeToString(
                DeleteShelfRequest(
                    strategy = null,
                    target_shelf_id = null,
                    deletion_batch_id = "11111111-1111-4111-8111-111111111111",
                ),
            )

        // An empty shelf legitimately has no strategy. Sending explicit null is
        // fine; what must never happen is the property vanishing because it was
        // defaulted.
        assertTrue(body.contains("\"strategy\":null"))
        assertFalse(body.isBlank())
    }
}
