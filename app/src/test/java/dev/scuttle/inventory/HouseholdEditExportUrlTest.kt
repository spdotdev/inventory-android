package dev.scuttle.inventory

import dev.scuttle.inventory.ui.households.webExportUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GAP6-M6: pins the derivation of the web export URL from BuildConfig.BASE_URL's shape
 * (`.../api/v1/`) — a regression here would silently point the export hint at a broken
 * or wrong host instead of failing loudly.
 */
class HouseholdEditExportUrlTest {
    @Test
    fun strips_the_api_v1_suffix_and_appends_the_web_export_path() {
        assertEquals(
            "https://inventory.scuttle.dev/app/households/42/export",
            webExportUrl(42L, baseUrl = "https://inventory.scuttle.dev/api/v1/"),
        )
    }

    @Test
    fun works_against_the_real_build_config_base_url() {
        // Exercises the actual production BASE_URL shape, not just a hand-picked string —
        // catches a build.gradle.kts change to BASE_URL's format breaking the derivation.
        val url = webExportUrl(1L)
        assertTrue("expected an /app/households/.../export URL but was: $url", url.endsWith("/app/households/1/export"))
        assertFalse("BASE_URL's /api/v1/ suffix must be stripped: $url", url.contains("/api/v1"))
    }
}
