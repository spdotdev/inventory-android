package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.DeleteLocationRequest
import dev.scuttle.inventory.data.dto.DeleteShelfRequest
import dev.scuttle.inventory.data.dto.ReorderRequest
import dev.scuttle.inventory.data.dto.UpdateHouseholdRequest
import dev.scuttle.inventory.data.dto.UpdateLocationRequest
import dev.scuttle.inventory.data.dto.UpdateShelfRequest
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's Json has encodeDefaults=false: a property equal to its default is
 * silently OMITTED from the request body. For most request DTOs that's exactly
 * the bug the server 422s on (a missing `deletion_batch_id`, an unsent strategy
 * the server required, etc.) — so these DTOs must have NO defaulted properties.
 *
 * This asserts on the serializer descriptor rather than an encoded body string.
 * `isElementOptional` is true iff the property has a default value, so it is
 * exactly discriminating on the field a default was (or wasn't) added to — a
 * body-string check like `body.contains("deletion_batch_id")` passes as long as
 * ANY value is present, which stays true even when a *different* field on the
 * same DTO regresses to a default and silently vanishes.
 */
class DeleteRequestSerializationTest {
    @Test
    fun delete_shelf_request_has_no_defaulted_properties() {
        assertNoDefaultedProperties(DeleteShelfRequest.serializer().descriptor)
    }

    @Test
    fun delete_location_request_has_no_defaulted_properties() {
        assertNoDefaultedProperties(DeleteLocationRequest.serializer().descriptor)
    }

    @Test
    fun update_shelf_request_has_no_defaulted_properties() {
        assertNoDefaultedProperties(UpdateShelfRequest.serializer().descriptor)
    }

    @Test
    fun update_location_request_has_no_defaulted_properties() {
        assertNoDefaultedProperties(UpdateLocationRequest.serializer().descriptor)
    }

    @Test
    fun reorder_request_has_no_defaulted_properties() {
        assertNoDefaultedProperties(ReorderRequest.serializer().descriptor)
    }

    /**
     * UpdateHouseholdRequest is the deliberate exception (C-1): `name` MUST stay
     * defaulted to null, so a theme-only update omits it — the server's
     * `sometimes|required` rule then reads "don't touch the name". Sending an
     * explicit null there fails validation. `color`/`icon` are `sometimes|
     * nullable` on the server, where an explicit null means "clear the theme
     * back to the derived default", so THEY must stay un-defaulted. Pin the
     * asymmetry explicitly so it reads as deliberate, not as a regression of
     * the no-defaults rule everywhere else in this file.
     */
    @Test
    fun update_household_request_defaults_only_name() {
        val descriptor = UpdateHouseholdRequest.serializer().descriptor

        assertTrue(
            "name must default to null so a theme-only update omits the key",
            descriptor.isElementOptional(descriptor.getElementIndex("name")),
        )
        assertFalse(
            "color must not be defaulted: an explicit null clears the theme",
            descriptor.isElementOptional(descriptor.getElementIndex("color")),
        )
        assertFalse(
            "icon must not be defaulted: an explicit null clears the theme",
            descriptor.isElementOptional(descriptor.getElementIndex("icon")),
        )
    }

    private fun assertNoDefaultedProperties(descriptor: SerialDescriptor) {
        for (i in 0 until descriptor.elementsCount) {
            assertFalse(
                "${descriptor.serialName}.${descriptor.getElementName(i)} must not have a default value: " +
                    "encodeDefaults=false would silently drop it from the request body",
                descriptor.isElementOptional(i),
            )
        }
    }
}
