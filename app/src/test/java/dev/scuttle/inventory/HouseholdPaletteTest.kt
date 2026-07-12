package dev.scuttle.inventory

import dev.scuttle.inventory.ui.theme.FROST_DARK_SURFACE_VARIANT_ARGB
import dev.scuttle.inventory.ui.theme.FROST_LIGHT_SURFACE_VARIANT_ARGB
import dev.scuttle.inventory.ui.theme.HOUSEHOLD_ACCENT_COUNT
import dev.scuttle.inventory.ui.theme.householdAccentDarkArgb
import dev.scuttle.inventory.ui.theme.householdAccentLightArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The household accents are used two ways, and only one of them is a contrast
 * problem:
 *
 *  - behind an avatar, as a 28%-alpha wash under an `onSurface` icon — the icon
 *    carries the contrast, so any hue works;
 *  - as a **solid bar fill** on the dashboard chart, which is a graphical object
 *    and needs >= 3:1 against the `surfaceVariant` track it sits on (WCAG 2.1
 *    non-text contrast).
 *
 * The dark palette is a set of pastels chosen to sit on dark navy; on the light
 * theme those same pastels wash out (amber #FCD34D on the #DCECF6 track is ~1.5:1).
 * Hence the parallel light palette. These tests pin that down so a hue added or
 * retuned later can't silently ship an unreadable bar.
 *
 * Compose-free on purpose: unit tests have no Compose on the classpath, so the
 * palette is stored as ARGB longs and lifted to `Color` in HouseholdTheme.kt.
 */
class HouseholdPaletteTest {
    /** WCAG 2.1 relative luminance. */
    private fun luminance(argb: Long): Double {
        fun channel(c: Long): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }

        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(
        a: Long,
        b: Long,
    ): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sorted().reversed()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** WCAG 2.1 SC 1.4.11 — non-text (graphical) contrast. */
    private val minGraphicalContrast = 3.0

    @Test
    fun light_and_dark_palettes_describe_the_same_households() {
        assertEquals(householdAccentDarkArgb.keys, householdAccentLightArgb.keys)
        assertEquals(HOUSEHOLD_ACCENT_COUNT, householdAccentDarkArgb.size)
        assertEquals(HOUSEHOLD_ACCENT_COUNT, householdAccentLightArgb.size)
    }

    @Test
    fun every_light_accent_is_readable_as_a_bar_on_the_light_track() {
        householdAccentLightArgb.forEach { (key, argb) ->
            val ratio = contrast(argb, FROST_LIGHT_SURFACE_VARIANT_ARGB)
            assertTrue(
                "light accent '$key' is ${"%.2f".format(ratio)}:1 on the light track, " +
                    "needs >= $minGraphicalContrast:1",
                ratio >= minGraphicalContrast,
            )
        }
    }

    @Test
    fun every_dark_accent_is_readable_as_a_bar_on_the_dark_track() {
        householdAccentDarkArgb.forEach { (key, argb) ->
            val ratio = contrast(argb, FROST_DARK_SURFACE_VARIANT_ARGB)
            assertTrue(
                "dark accent '$key' is ${"%.2f".format(ratio)}:1 on the dark track, " +
                    "needs >= $minGraphicalContrast:1",
                ratio >= minGraphicalContrast,
            )
        }
    }

    /**
     * The pastels are kept for the avatar wash but must NOT be used as light-theme
     * bars. If someone "simplifies" the two palettes back into one, this fails.
     */
    @Test
    fun the_dark_pastels_would_fail_as_light_theme_bars() {
        val washedOut =
            householdAccentDarkArgb.count { (_, argb) ->
                contrast(argb, FROST_LIGHT_SURFACE_VARIANT_ARGB) < minGraphicalContrast
            }
        assertTrue(
            "expected the dark pastels to be unusable as light bars (that's why the " +
                "light palette exists), but $washedOut of ${householdAccentDarkArgb.size} failed",
            washedOut >= householdAccentDarkArgb.size / 2,
        )
    }
}
