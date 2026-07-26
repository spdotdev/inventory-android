package dev.scuttle.inventory.ui.theme

import androidx.compose.ui.graphics.Color

// Frost palette (B · Frost), mirrored from frost-light.png / frost-dark.png design tokens.

// Dark theme — icy-blue accent on dark navy
val FrostAccent = Color(0xFF7DD3FC) // sky-blue, high contrast on dark
val FrostOnAccent = Color(0xFF06283B)

val FrostDarkBackground = Color(0xFF0C1822)
val FrostDarkSurface = Color(0xFF10212E)
val FrostDarkSurfaceVariant = Color(FROST_DARK_SURFACE_VARIANT_ARGB)
val FrostDarkOnSurface = Color(0xFFEAF6FF)
val FrostDarkOnSurfaceVariant = Color(0xFFB0CCE0)

// Light theme — deeper teal primary on near-white cards, muted ice-blue background
val FrostLightPrimary = Color(0xFF2298BA) // medium teal-blue, ~4.5:1 on white
val FrostLightOnPrimary = Color(0xFFFFFFFF)
val FrostLightBackground = Color(0xFFC2D5E3) // muted steel-blue (matches ref bg)
val FrostLightSurface = Color(0xFFF5FAFD) // near-white card surface

// Built from the ARGB constants in HouseholdPalette.kt (not a duplicate literal) so the
// household-accent contrast test measures against the track the bars actually sit on.
val FrostLightSurfaceVariant = Color(FROST_LIGHT_SURFACE_VARIANT_ARGB) // chart bg / chips
val FrostLightOnSurface = Color(0xFF0D2436)
val FrostLightOnSurfaceVariant = Color(0xFF3D5A6E)

// Move-button accent — distinct from the icy-blue primary so the relocate action reads
// as its own affordance. Same amber works in both themes; dark on-color gives ~8:1 contrast.
val FrostMoveAccent = Color(0xFFFBBF24)
val FrostOnMoveAccent = Color(0xFF3D2B00)

/**
 * The one alpha for "this thing is missing/at-zero" container tints — the product
 * row set the reference look; the dashboard missing card, storage-list warning
 * rows and the missing-items list all reuse it so the same state reads as the
 * same red everywhere (user decision 2026-07-27).
 */
const val WARNING_TINT_ALPHA = 0.3f

/**
 * "Running low" tint — a muted grey-leaning dark orange, always used at
 * [WARNING_TINT_ALPHA] so it sits at exactly the same visual weight as the
 * missing-item red (user decision 2026-07-27).
 */
val LowStockWarnOrange = Color(0xFFB45309)
