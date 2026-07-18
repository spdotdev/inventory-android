package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.SearchResultDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4: `shelf_is_system` must decode fine both when the server sends it (new backend) and when
 * it doesn't (old backend, field not shipped yet) — see [SearchResultDto]'s doc comment for the
 * same backward-compat pattern used by `ShelfDto.is_system`.
 */
class SearchResultDtoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodes_shelf_is_system_true_when_the_server_sends_it() {
        val decoded =
            json.decodeFromString(
                SearchResultDto.serializer(),
                """{"id":1,"name":"Milk","quantity":2,"location":"Kitchen","shelf":"Unsorted",""" +
                    """"path":"Kitchen › Unsorted","shelf_is_system":true}""",
            )

        assertTrue(decoded.shelf_is_system)
    }

    @Test
    fun decodes_shelf_is_system_false_when_the_server_sends_it() {
        val decoded =
            json.decodeFromString(
                SearchResultDto.serializer(),
                """{"id":1,"name":"Milk","quantity":2,"location":"Kitchen","shelf":"Fridge",""" +
                    """"path":"Kitchen › Fridge","shelf_is_system":false}""",
            )

        assertFalse(decoded.shelf_is_system)
    }

    @Test
    fun defaults_shelf_is_system_to_false_when_an_older_server_omits_the_field() {
        val decoded =
            json.decodeFromString(
                SearchResultDto.serializer(),
                """{"id":1,"name":"Milk","quantity":2,"location":"Kitchen","shelf":"Fridge",""" +
                    """"path":"Kitchen › Fridge"}""",
            )

        assertFalse(decoded.shelf_is_system)
    }
}
