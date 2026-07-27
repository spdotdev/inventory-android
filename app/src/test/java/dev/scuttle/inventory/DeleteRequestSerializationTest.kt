package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.CreateLocationRequest
import dev.scuttle.inventory.data.dto.DeleteHouseholdRequest
import dev.scuttle.inventory.data.dto.DeleteLocationRequest
import dev.scuttle.inventory.data.dto.DeleteShelfRequest
import dev.scuttle.inventory.data.dto.ReorderRequest
import dev.scuttle.inventory.data.dto.UpdateHouseholdRequest
import dev.scuttle.inventory.data.dto.UpdateLocationRequest
import dev.scuttle.inventory.data.dto.UpdateShelfRequest
import dev.scuttle.inventory.di.NetworkModule
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's Json (see [NetworkModule.provideJson] — used directly here, not
 * reimplemented, so this test cannot drift from the real wire config) has
 * `encodeDefaults = false` (the kotlinx-serialization default) AND
 * `explicitNulls = true` (also the default — deliberately NOT turned off, see
 * below). Those two settings combine non-obviously:
 *
 * - A property EQUAL TO ITS DEFAULT is OMITTED from the body (encodeDefaults=false).
 * - A property with NO default is ALWAYS ENCODED, even when its value is null
 *   (explicitNulls=true only suppresses nulls that come from a *default*).
 *
 * `strategy`/`target_location_id`/`target_shelf_id` used to have no default, so an
 * empty-container delete (which leaves them null) put
 * `{"strategy":null,"target_location_id":null,...}` on the wire — a PRESENT key
 * with a null value. The server's `Rule::requiredIf` validates a present-but-null
 * key as a *type* error, not as "absent", so that 422'd on every delete except
 * `move_contents`/`move_products` (the only strategies that also supply a real,
 * non-null target). Giving them `= null` defaults fixes this: a value still equal
 * to that default is omitted, exactly like a field that was never set.
 *
 * These tests assert the actual encoded BYTES for every strategy, not just
 * "does this property have a default" — asserting only the descriptor shape is
 * what let the broken version of this DTO ship in the first place (the old
 * `assertNoDefaultedProperties` check happily "pinned" no-default properties as
 * correct, without ever encoding an instance to see what they produced on the
 * wire). `deletion_batch_id` staying required (no default, no way to omit it) is
 * still pinned by construction: it's the one field with no default in the class,
 * so leaving it out of a call site would already fail to compile.
 */
class DeleteRequestSerializationTest {
    private val json = NetworkModule.provideJson()

    // --- DeleteLocationRequest ---------------------------------------------

    @Test
    fun delete_location_request_for_an_empty_location_omits_strategy_and_target() {
        val body = json.encodeToString(DeleteLocationRequest(deletion_batch_id = "batch-1"))

        assertEquals("""{"deletion_batch_id":"batch-1"}""", body)
    }

    @Test
    fun delete_location_request_for_delete_contents_omits_only_the_target() {
        val body =
            json.encodeToString(
                DeleteLocationRequest(strategy = "delete_contents", deletion_batch_id = "batch-1"),
            )

        assertEquals("""{"strategy":"delete_contents","deletion_batch_id":"batch-1"}""", body)
    }

    @Test
    fun delete_location_request_for_move_contents_encodes_the_target() {
        val body =
            json.encodeToString(
                DeleteLocationRequest(
                    strategy = "move_contents",
                    target_location_id = 5L,
                    deletion_batch_id = "batch-1",
                ),
            )

        assertEquals(
            """{"strategy":"move_contents","target_location_id":5,"deletion_batch_id":"batch-1"}""",
            body,
        )
    }

    // --- DeleteShelfRequest --------------------------------------------------

    @Test
    fun delete_shelf_request_for_an_empty_shelf_omits_strategy_and_target() {
        val body = json.encodeToString(DeleteShelfRequest(deletion_batch_id = "batch-2"))

        assertEquals("""{"deletion_batch_id":"batch-2"}""", body)
    }

    @Test
    fun delete_shelf_request_for_unsort_products_omits_only_the_target() {
        val body =
            json.encodeToString(
                DeleteShelfRequest(strategy = "unsort_products", deletion_batch_id = "batch-2"),
            )

        assertEquals("""{"strategy":"unsort_products","deletion_batch_id":"batch-2"}""", body)
    }

    @Test
    fun delete_shelf_request_for_move_products_encodes_the_target() {
        val body =
            json.encodeToString(
                DeleteShelfRequest(
                    strategy = "move_products",
                    target_shelf_id = 7L,
                    deletion_batch_id = "batch-2",
                ),
            )

        assertEquals(
            """{"strategy":"move_products","target_shelf_id":7,"deletion_batch_id":"batch-2"}""",
            body,
        )
    }

    // --- deletion_batch_id must stay required (no way to omit it) -----------

    @Test
    fun delete_location_request_deletion_batch_id_is_not_optional() {
        val descriptor = DeleteLocationRequest.serializer().descriptor
        assertFalse(descriptor.isElementOptional(descriptor.getElementIndex("deletion_batch_id")))
    }

    @Test
    fun delete_shelf_request_deletion_batch_id_is_not_optional() {
        val descriptor = DeleteShelfRequest.serializer().descriptor
        assertFalse(descriptor.isElementOptional(descriptor.getElementIndex("deletion_batch_id")))
    }

    // --- DeleteHouseholdRequest: a typed confirmation, not a patch — `name` must
    // ALWAYS be encoded (even if it happened to equal some default), so unlike
    // Delete{Location,Shelf}Request it has no defaulted properties at all. ------

    @Test
    fun delete_household_request_has_no_defaulted_properties() {
        assertNoDefaultedProperties(DeleteHouseholdRequest.serializer().descriptor)
    }

    @Test
    fun delete_household_request_encodes_the_exact_name() {
        val body = json.encodeToString(DeleteHouseholdRequest(name = "Garage"))

        assertEquals("""{"name":"Garage"}""", body)
    }

    // --- Other request DTOs: unaffected by the above, still no defaults ------

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
     *
     * This is the OPPOSITE shape from DeleteLocationRequest/DeleteShelfRequest
     * above, and that's the point: this file must never "fix" this asymmetry by
     * globally flipping `explicitNulls` or by giving `color`/`icon` defaults —
     * either change would silently break the shipped household colour/icon
     * clear-to-default feature.
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

    /**
     * The descriptor-shape test above only pins the serializer's metadata — it
     * would still pass if `color`/`icon` were given a default that happened not
     * to break anything else, or if `name` lost its default while every call
     * site coincidentally still worked in tests that never look at the wire.
     * That's exactly the gap the doc comment above `assertNoDefaultedProperties`
     * describes: "pinning" a no-default property as correct without ever
     * encoding an instance. These two tests encode a real instance the way each
     * production call site actually builds one (HouseholdsViewModel.update /
     * updateTheme) and assert the literal bytes for BOTH directions of the
     * asymmetry, so a regression in either direction shows up here as a diff in
     * the JSON string, not just in descriptor shape.
     */
    @Test
    fun renaming_a_household_encodes_its_current_theme_instead_of_clearing_it() {
        // Mirrors the edit screen's Save-name action: color/icon are passed
        // through as the household's CURRENT keys (not null) because they have
        // no default and are always encoded — an explicit null here would clear
        // the theme server-side (`sometimes|nullable`). This is what makes a
        // rename side-effect-free on the theme.
        val body =
            json.encodeToString(
                UpdateHouseholdRequest(name = "House", color = "teal", icon = "cottage"),
            )

        assertEquals("""{"name":"House","color":"teal","icon":"cottage"}""", body)
    }

    @Test
    fun clearing_the_theme_omits_the_name_key_entirely() {
        // Mirrors updateTheme()'s call to update(name = null, ...): name's
        // default drops the key so the server (`sometimes|required`) never sees
        // an explicit null and never touches the name. If `name` lost its
        // default, this would encode `"name":null` and 422. If `color`/`icon`
        // gained a default, this would encode `{}` and silently no-op instead
        // of clearing the theme.
        val body =
            json.encodeToString(
                UpdateHouseholdRequest(name = null, color = null, icon = null),
            )

        assertEquals("""{"color":null,"icon":null}""", body)
    }

    /**
     * UpdateShelfRequest mirrors UpdateHouseholdRequest's exact asymmetry (see
     * that class's own descriptor test above and CLAUDE.md's "Deletes" section):
     * `name` defaults to null (an absent key = "don't touch the name" under the
     * server's `sometimes|required` rule), `color`/`icon` do not (an explicit
     * null clears the theme under `sometimes|nullable`). Pinned here at the
     * descriptor level, then at the byte level below for every production call
     * shape (name-only, color+icon-only, combined, and explicit-null clear) —
     * a descriptor-shape-only test already let this exact bug ship once (the
     * doc comment above `assertNoDefaultedProperties` explains why).
     */
    @Test
    fun update_shelf_request_defaults_only_name() {
        val descriptor = UpdateShelfRequest.serializer().descriptor

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

    @Test
    fun renaming_a_shelf_encodes_its_current_theme_instead_of_clearing_it() {
        // Mirrors ShelfEditScreen's Save-name action / ShelvesViewModel.update:
        // color/icon are passed through as the shelf's CURRENT keys (not null)
        // because they have no default and are always encoded — an explicit
        // null here would clear the theme server-side. This is what makes a
        // rename side-effect-free on the theme.
        val body =
            json.encodeToString(
                UpdateShelfRequest(name = "Freezer top", color = "teal", icon = "cottage"),
            )

        assertEquals("""{"name":"Freezer top","color":"teal","icon":"cottage"}""", body)
    }

    @Test
    fun a_shelf_theme_only_update_omits_the_name_key_entirely() {
        // Mirrors ShelvesViewModel.updateTheme()'s call to update(name = null,
        // ...): name's default drops the key so the server (`sometimes|
        // required`) never sees an explicit null and never touches the name.
        val body =
            json.encodeToString(
                UpdateShelfRequest(name = null, color = "sky", icon = "box"),
            )

        assertEquals("""{"color":"sky","icon":"box"}""", body)
    }

    @Test
    fun clearing_a_shelfs_theme_omits_the_name_key_and_encodes_explicit_nulls() {
        // Mirrors the "Default" TextButton's updateTheme(color = null, icon =
        // null) call: if `name` lost its default, this would encode
        // `"name":null` and 422; if `color`/`icon` gained a default, this
        // would encode `{}` and silently no-op instead of clearing the theme.
        val body =
            json.encodeToString(
                UpdateShelfRequest(name = null, color = null, icon = null),
            )

        assertEquals("""{"color":null,"icon":null}""", body)
    }

    @Test
    fun renaming_a_shelf_with_no_prior_theme_still_encodes_explicit_null_color_and_icon() {
        // A shelf that never had a theme (color/icon both null in ShelfDto) and
        // is renamed for the first time: selectedColor/selectedIcon in
        // ShelfEditScreen stay null, and per the asymmetry above those ARE
        // always encoded (no default) — distinct from a household/shelf that
        // HAD a theme and is being renamed (the test above), where the encoded
        // value is the non-null current key instead. Both shapes must round-trip
        // correctly: a null here means "no theme", not "clear this shelf's
        // theme" — server-side `sometimes|nullable` treats them identically
        // (both are a no-op when the shelf already has no theme), so this is
        // safe either way, but the wire bytes must still be exactly this.
        val body =
            json.encodeToString(
                UpdateShelfRequest(name = "Top shelf", color = null, icon = null),
            )

        assertEquals("""{"name":"Top shelf","color":null,"icon":null}""", body)
    }

    /**
     * UpdateLocationRequest mirrors UpdateShelfRequest's exact asymmetry (see
     * that class's own descriptor test above and CLAUDE.md's "Deletes"
     * section): `name`/`type` default to null (an absent key = "don't touch
     * this field" under the server's `sometimes` rule), `color`/`icon` do not
     * (an explicit null clears the theme under `sometimes|nullable`). Pinned
     * here at the descriptor level, then at the byte level below for every
     * production call shape (name+type-only, color+icon-only, combined, and
     * explicit-null clear).
     */
    @Test
    fun update_location_request_defaults_only_name_and_type() {
        val descriptor = UpdateLocationRequest.serializer().descriptor

        assertTrue(
            "name must default to null so a theme-only update omits the key",
            descriptor.isElementOptional(descriptor.getElementIndex("name")),
        )
        assertTrue(
            "type must default to null so a theme-only update omits the key",
            descriptor.isElementOptional(descriptor.getElementIndex("type")),
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

    @Test
    fun renaming_a_location_encodes_its_current_theme_instead_of_clearing_it() {
        // Mirrors StorageEditScreen's Save-name action / StorageOverviewViewModel.update:
        // color/icon are passed through as the location's CURRENT keys (not null)
        // because they have no default and are always encoded — an explicit
        // null here would clear the theme server-side. This is what makes a
        // rename/type-change side-effect-free on the theme.
        val body =
            json.encodeToString(
                UpdateLocationRequest(name = "Walk-in Fridge", type = "fridge", color = "teal", icon = "cottage"),
            )

        assertEquals("""{"name":"Walk-in Fridge","type":"fridge","color":"teal","icon":"cottage"}""", body)
    }

    @Test
    fun a_location_theme_only_update_omits_the_name_and_type_keys_entirely() {
        // Mirrors StorageOverviewViewModel.updateTheme()'s call to update(name =
        // null, type = null, ...): both defaults drop their keys so the server
        // (`sometimes`) never sees an explicit null and never touches either.
        val body =
            json.encodeToString(
                UpdateLocationRequest(name = null, type = null, color = "sky", icon = "box"),
            )

        assertEquals("""{"color":"sky","icon":"box"}""", body)
    }

    @Test
    fun clearing_a_locations_theme_omits_the_name_and_type_keys_and_encodes_explicit_nulls() {
        // Mirrors the "Default" TextButton's updateTheme(color = null, icon =
        // null) call: if `name`/`type` lost their default, this would encode
        // explicit nulls for them and 422; if `color`/`icon` gained a default,
        // this would encode `{}` and silently no-op instead of clearing the theme.
        val body =
            json.encodeToString(
                UpdateLocationRequest(name = null, type = null, color = null, icon = null),
            )

        assertEquals("""{"color":null,"icon":null}""", body)
    }

    @Test
    fun renaming_a_location_with_no_prior_theme_still_encodes_explicit_null_color_and_icon() {
        // A location that never had a theme (color/icon both null in
        // LocationDto) and is renamed for the first time: selectedColor/
        // selectedIcon in StorageEditScreen stay null, and per the asymmetry
        // above those ARE always encoded (no default) — distinct from a
        // location that HAD a theme and is being renamed (the test above),
        // where the encoded value is the non-null current key instead.
        val body =
            json.encodeToString(
                UpdateLocationRequest(name = "Top Shelf Pantry", type = "pantry", color = null, icon = null),
            )

        assertEquals("""{"name":"Top Shelf Pantry","type":"pantry","color":null,"icon":null}""", body)
    }

    /**
     * CreateLocationRequest gained optional `color`/`icon` this branch (the
     * add-storage sheets' new theme pickers). Unlike UpdateLocationRequest,
     * BOTH default to null — there is no "preserve the current theme" case on
     * create, so "unset" and "explicit null" collapse to the same wire shape
     * (omit the key), which is exactly what the server's `sometimes|nullable`
     * create validation already accepts. Asserted at the byte level per the
     * "Assert the bytes, not the shape" lesson above: a descriptor-shape check
     * alone would not catch a regression where these keys stopped being
     * dropped for an unthemed create.
     */
    @Test
    fun create_location_request_with_a_theme_encodes_color_and_icon() {
        val body =
            json.encodeToString(
                CreateLocationRequest(name = "Walk-in Fridge", type = "fridge", color = "teal", icon = "cottage"),
            )

        assertEquals("""{"name":"Walk-in Fridge","type":"fridge","color":"teal","icon":"cottage"}""", body)
    }

    @Test
    fun create_location_request_without_a_theme_omits_color_and_icon_entirely() {
        val body = json.encodeToString(CreateLocationRequest(name = "Walk-in Fridge", type = "fridge"))

        assertEquals("""{"name":"Walk-in Fridge","type":"fridge"}""", body)
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
