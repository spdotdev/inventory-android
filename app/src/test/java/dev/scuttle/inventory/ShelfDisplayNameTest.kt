package dev.scuttle.inventory

import dev.scuttle.inventory.ui.common.shelfDisplayNameRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the system-shelf localization mapping (final review, ALSO FIX):
 * ShelfDto.name's own doc comment always claimed "the client localises the label
 * off [is_system]" — nothing actually wired that until this — mirrors
 * StorageTypeLabelTest's split between the pure resource lookup and the untested
 * composable wrapper (no Robolectric/compose-ui-test on this module's JVM classpath).
 */
class ShelfDisplayNameTest {
    @Test
    fun a_system_shelf_maps_to_the_unsorted_resource() {
        assertEquals(R.string.shelf_unsorted, shelfDisplayNameRes(isSystem = true))
    }

    @Test
    fun a_normal_shelf_has_no_override_resource() {
        assertNull(shelfDisplayNameRes(isSystem = false))
    }
}
