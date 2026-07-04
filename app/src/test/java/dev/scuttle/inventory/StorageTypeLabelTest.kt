package dev.scuttle.inventory

import dev.scuttle.inventory.ui.common.storageTypeLabelRes
import dev.scuttle.inventory.ui.storage.STORAGE_TYPES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the storage-type transport→resource mapping and, crucially, that it stays
 * in lockstep with STORAGE_TYPES (the values the add-sheet offers) — a new picker
 * option without a label would otherwise silently fall back to the raw enum.
 */
class StorageTypeLabelTest {

    @Test
    fun known_types_map_to_string_resources() {
        assertEquals(R.string.storage_type_freezer, storageTypeLabelRes("freezer"))
        assertEquals(R.string.storage_type_fridge, storageTypeLabelRes("fridge"))
        assertEquals(R.string.storage_type_pantry, storageTypeLabelRes("pantry"))
        assertEquals(R.string.storage_type_other, storageTypeLabelRes("other"))
    }

    @Test
    fun every_offered_picker_type_has_a_label() {
        STORAGE_TYPES.forEach { type ->
            assertNotNull("STORAGE_TYPES value '$type' has no localized label", storageTypeLabelRes(type))
        }
    }

    @Test
    fun unknown_type_falls_back_to_no_resource() {
        assertNull(storageTypeLabelRes("spaceship"))
    }
}
