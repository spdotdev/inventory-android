package dev.scuttle.inventory.ui.theme

/**
 * Raw household accent data, as ARGB longs. Deliberately free of Compose types —
 * same reason as HouseholdThemeIndex.kt: unit tests run on a plain JVM with no
 * Compose on the classpath, and the contrast of these hues is exactly the thing
 * worth testing (see HouseholdPaletteTest). HouseholdTheme.kt lifts them to `Color`.
 *
 * Two palettes, because the accents serve two roles with different contrast needs:
 *
 *  - **Avatar wash** — the accent at 28% alpha under an `onSurface`-tinted icon.
 *    The icon carries the contrast, so the pastels work on either theme.
 *  - **Bar fill** (dashboard chart) — a solid graphical object, so WCAG 2.1 SC
 *    1.4.11 wants >= 3:1 against the `surfaceVariant` track. The pastels are tuned
 *    for dark navy and wash out on the light track (amber #FCD34D on #DCECF6 is
 *    ~1.5:1), so the light theme uses a darker tone of each hue.
 *
 * This mirrors what Frost already does for its own accent: FrostAccent #7DD3FC on
 * dark becomes FrostLightPrimary #2298BA on light.
 *
 * Key order doubles as the id-derived fallback palette (HouseholdThemeIndex) and is
 * shared with the server's HouseholdColor enum — **don't reorder, and keep the two
 * maps keyed identically.**
 */
internal val householdAccentDarkArgb =
    linkedMapOf(
        "sky" to 0xFF7DD3FC, // Frost accent
        "teal" to 0xFF5EEAD4,
        "indigo" to 0xFFA5B4FC,
        "pink" to 0xFFF9A8D4,
        "amber" to 0xFFFCD34D,
        "green" to 0xFF86EFAC,
        "violet" to 0xFFC4B5FD,
        "orange" to 0xFFFDBA74,
    )

/** The same hues, darkened for the light theme so a solid bar stays readable. */
internal val householdAccentLightArgb =
    linkedMapOf(
        "sky" to 0xFF0284C7,
        "teal" to 0xFF0D9488,
        "indigo" to 0xFF4F46E5,
        "pink" to 0xFFDB2777,
        "amber" to 0xFFB45309,
        "green" to 0xFF15803D,
        "violet" to 0xFF7C3AED,
        "orange" to 0xFFC2410C,
    )

// The chart track the bars sit on. Color.kt builds its surfaceVariant tokens from
// these, so the contrast test and the theme can't drift apart.
internal const val FROST_LIGHT_SURFACE_VARIANT_ARGB = 0xFFDCECF6
internal const val FROST_DARK_SURFACE_VARIANT_ARGB = 0xFF182C3A
