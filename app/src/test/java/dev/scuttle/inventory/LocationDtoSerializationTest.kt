package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.di.NetworkModule
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LocationDto gained nullable `color`/`icon` this branch (the storage-theming
 * backend contract) — both DEFAULT to null so a pre-theming server response
 * (or any payload that simply omits them) still decodes instead of throwing
 * MissingFieldException, exactly like ShelfDto's/HouseholdDto's own
 * color/icon fields.
 */
class LocationDtoSerializationTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun decodes_a_location_with_no_color_or_icon_keys_present() {
        val location =
            json.decodeFromString<LocationDto>(
                """{"id":1,"name":"Fridge","type":"fridge"}""",
            )

        assertNull(location.color)
        assertNull(location.icon)
    }

    @Test
    fun decodes_a_location_with_explicit_null_color_and_icon() {
        val location =
            json.decodeFromString<LocationDto>(
                """{"id":1,"name":"Fridge","type":"fridge","color":null,"icon":null}""",
            )

        assertNull(location.color)
        assertNull(location.icon)
    }

    @Test
    fun decodes_a_location_with_a_theme() {
        val location =
            json.decodeFromString<LocationDto>(
                """{"id":1,"name":"Fridge","type":"fridge","color":"teal","icon":"cottage"}""",
            )

        assertEquals("teal", location.color)
        assertEquals("cottage", location.icon)
    }
}
