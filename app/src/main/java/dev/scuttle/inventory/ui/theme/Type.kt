package dev.scuttle.inventory.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.scuttle.inventory.R

// Plus Jakarta Sans ships as a variable font; pin each weight via FontVariation.
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) =
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

val PlusJakartaSans =
    FontFamily(
        jakarta(400),
        jakarta(500),
        jakarta(600),
        jakarta(700),
        jakarta(800),
    )

// Space Mono — used for join codes (D-021).
val SpaceMono =
    FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )

// Titles/headlines get EXPLICIT heavier weights than Material's defaults
// (titleLarge is W400 stock, titleMedium W500): issue #32 follow-up — with the
// system "Bold text" setting on, font_weight_adjustment (+300, clamped at the
// family's heaviest cut) collapsed W400 body and W400/500 titles onto the same
// rendered weight, flattening the hierarchy. W600/700 titles stay one step
// heavier than body both normally (400 vs 600/700) and under the adjustment
// (700 vs 800-clamped). This works WITH the accessibility setting, never
// against it.
private fun Typography.withFamily(family: FontFamily) =
    copy(
        displayLarge = displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )

val InventoryTypography = Typography().withFamily(PlusJakartaSans)
