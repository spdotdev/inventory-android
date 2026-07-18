package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.UpdateProductRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression: UpdateProductRequest used property defaults, and the app's Json has
 * encodeDefaults=false — a value equal to its default was OMITTED from the PATCH
 * body, so the API's `sometimes` rules kept the old value. Clearing a description
 * or code, unchecking is_mandatory, and clearing low_stock_threshold silently
 * didn't persist. Every field must always be encoded (null as explicit null).
 */
class UpdateProductRequestSerializationTest {
    private val json = Json { ignoreUnknownKeys = true } // mirrors NetworkModule

    @Test
    fun cleared_fields_are_encoded_as_explicit_nulls_and_false() {
        val body =
            json.encodeToString(
                UpdateProductRequest.serializer(),
                UpdateProductRequest(
                    name = "Milk",
                    description = null,
                    code = null,
                    is_mandatory = false,
                    is_starred = false,
                    low_stock_threshold = null,
                ),
            )

        val expected =
            """{"name":"Milk","description":null,"code":null,""" +
                """"is_mandatory":false,"is_starred":false,"low_stock_threshold":null}"""
        assertEquals(expected, body)
    }
}
