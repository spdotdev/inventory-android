package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.di.NetworkModule
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ShelfDto gained nullable `color`/`icon` this branch (the shelf-theming
 * backend contract) — both DEFAULT to null so a pre-theming server response
 * (or any payload that simply omits them) still decodes instead of throwing
 * MissingFieldException, exactly like HouseholdDto's own color/icon fields.
 */
class ShelfDtoSerializationTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun decodes_a_shelf_with_no_color_or_icon_keys_present() {
        val shelf =
            json.decodeFromString<ShelfDto>(
                """{"id":1,"name":"Top shelf","location_id":10}""",
            )

        assertNull(shelf.color)
        assertNull(shelf.icon)
    }

    @Test
    fun decodes_a_shelf_with_explicit_null_color_and_icon() {
        val shelf =
            json.decodeFromString<ShelfDto>(
                """{"id":1,"name":"Top shelf","location_id":10,"color":null,"icon":null}""",
            )

        assertNull(shelf.color)
        assertNull(shelf.icon)
    }

    @Test
    fun decodes_a_shelf_with_a_theme() {
        val shelf =
            json.decodeFromString<ShelfDto>(
                """{"id":1,"name":"Top shelf","location_id":10,"color":"teal","icon":"cottage"}""",
            )

        assertEquals("teal", shelf.color)
        assertEquals("cottage", shelf.icon)
    }
}
