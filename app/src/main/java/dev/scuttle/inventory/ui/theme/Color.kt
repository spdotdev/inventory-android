package dev.scuttle.inventory.ui.theme

import androidx.compose.ui.graphics.Color

// Frost palette (B · Frost), mirrored from frost-light.png / frost-dark.png design tokens.

// Dark theme — icy-blue accent on dark navy
val FrostAccent = Color(0xFF7DD3FC) // sky-blue, high contrast on dark
val FrostOnAccent = Color(0xFF06283B)

val FrostDarkBackground = Color(0xFF0C1822)
val FrostDarkSurface = Color(0xFF10212E)
val FrostDarkSurfaceVariant = Color(0xFF182C3A)
val FrostDarkOnSurface = Color(0xFFEAF6FF)
val FrostDarkOnSurfaceVariant = Color(0xFFB0CCE0)

// Light theme — deeper teal primary on near-white cards, muted ice-blue background
val FrostLightPrimary = Color(0xFF2298BA) // medium teal-blue, ~4.5:1 on white
val FrostLightOnPrimary = Color(0xFFFFFFFF)
val FrostLightBackground = Color(0xFFC2D5E3) // muted steel-blue (matches ref bg)
val FrostLightSurface = Color(0xFFF5FAFD) // near-white card surface
val FrostLightSurfaceVariant = Color(0xFFDCECF6) // slightly tinted for chart bg / chips
val FrostLightOnSurface = Color(0xFF0D2436)
val FrostLightOnSurfaceVariant = Color(0xFF3D5A6E)
