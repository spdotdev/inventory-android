package dev.scuttle.inventory

import dev.scuttle.inventory.ui.settings.parseJoinCode
import org.junit.Assert.assertEquals
import org.junit.Test

class JoinCodeTest {
    @Test
    fun invite_link_from_a_scanned_qr_yields_the_bare_code() {
        assertEquals("ABCD-2345", parseJoinCode("https://inventory.scuttle.dev/join/ABCD-2345"))
    }

    @Test
    fun invite_link_survives_a_trailing_slash_query_or_fragment() {
        assertEquals("ABCD-2345", parseJoinCode("https://inventory.scuttle.dev/join/ABCD-2345/"))
        assertEquals("ABCD-2345", parseJoinCode("https://inventory.scuttle.dev/join/ABCD-2345?utm=qr"))
        assertEquals("ABCD-2345", parseJoinCode("https://inventory.scuttle.dev/join/ABCD-2345#top"))
    }

    @Test
    fun a_bare_code_is_passed_through() {
        assertEquals("ABCD-2345", parseJoinCode("ABCD-2345"))
    }

    @Test
    fun a_typed_code_is_normalised_to_the_form_the_api_stores() {
        assertEquals("ABCD-2345", parseJoinCode("  abcd-2345 "))
        assertEquals("ABCD-2345", parseJoinCode("abcd2345"))
    }

    @Test
    fun anything_unrecognised_is_left_alone_for_the_server_to_reject() {
        assertEquals("NOT A CODE", parseJoinCode("not a code"))
        assertEquals("", parseJoinCode("   "))
    }
}
