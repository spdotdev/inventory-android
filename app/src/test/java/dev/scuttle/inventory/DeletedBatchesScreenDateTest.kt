package dev.scuttle.inventory

import dev.scuttle.inventory.ui.deleted.formatDeletedAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeletedBatchesScreenDateTest {
    @Test
    fun formats_a_valid_iso_instant() {
        val formatted = formatDeletedAt("2026-07-19T12:34:56Z")
        // Exact rendering is locale/zone-dependent (that's the point — user-local
        // display), so just confirm it actually parsed rather than falling back to
        // the raw string.
        assertNotEquals("2026-07-19T12:34:56Z", formatted)
    }

    @Test
    fun falls_back_to_the_raw_string_on_a_malformed_value_instead_of_crashing() {
        assertEquals("not-a-date", formatDeletedAt("not-a-date"))
    }
}
