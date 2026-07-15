package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.ProductDeleteResponse
import dev.scuttle.inventory.di.NetworkModule
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * `ProductController::destroy()` mints a batch-of-one `deletion_batch_id` for a
 * solo product delete and returns it (unlike shelf/location deletes, where the
 * CLIENT mints the batch id and sends it in the request) — see the controller's
 * own doc comment in inventory-laravel. `ProductApi.delete()` used to declare no
 * return type at all, so the body — and the batch id inside it — was silently
 * discarded and product delete was the one destructive action in the app with
 * no Undo.
 *
 * These tests decode the literal JSON bytes the real controller returns
 * (`return response()->json(['message' => ..., 'deletion_batch_id' => $batchId])`),
 * not just the DTO's descriptor shape — "assert the bytes, not the shape" is the
 * lesson `DeleteRequestSerializationTest` already had to learn once on this
 * branch (a descriptor-only assertion let a broken wire format ship as passing).
 */
class ProductDeleteResponseSerializationTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun decodes_the_real_controller_response_body() {
        val body = """{"message":"Product deleted.","deletion_batch_id":"11111111-1111-1111-1111-111111111111"}"""

        val response = json.decodeFromString<ProductDeleteResponse>(body)

        assertEquals("Product deleted.", response.message)
        assertEquals("11111111-1111-1111-1111-111111111111", response.deletion_batch_id)
    }

    @Test
    fun a_camelCase_key_does_not_decode_the_batch_id() {
        // Pins the wire name: this project has already shipped one Critical from a
        // camelCase/snake_case mismatch. If `deletion_batch_id` were ever renamed to
        // `deletionBatchId` (or the server's key changed), this must fail loudly
        // instead of silently defaulting/omitting the batch id.
        val body = """{"message":"Product deleted.","deletionBatchId":"11111111-1111-1111-1111-111111111111"}"""

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<ProductDeleteResponse>(body)
        }
    }
}
