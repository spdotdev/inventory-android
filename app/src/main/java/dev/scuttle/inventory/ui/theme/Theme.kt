package dev.scuttle.inventory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColors =
    darkColorScheme(
        primary = FrostAccent,
        onPrimary = FrostOnAccent,
        background = FrostDarkBackground,
        surface = FrostDarkSurface,
        surfaceVariant = FrostDarkSurfaceVariant,
        onSurface = FrostDarkOnSurface,
        onSurfaceVariant = FrostDarkOnSurfaceVariant,
        onBackground = FrostDarkOnSurface,
    )

private val LightColors =
    lightColorScheme(
        primary = FrostLightPrimary,
        onPrimary = FrostLightOnPrimary,
        background = FrostLightBackground,
        surface = FrostLightSurface,
        surfaceVariant = FrostLightSurfaceVariant,
        onSurface = FrostLightOnSurface,
        onSurfaceVariant = FrostLightOnSurfaceVariant,
        onBackground = FrostLightOnSurface,
    )

// frost-app.html `.card`: tinted-primary wash on dark, near-white translucent wash on light.
private val DarkFrostCardColors =
    FrostCardColors(
        container = FrostAccent.copy(alpha = 0.10f),
        border = FrostAccent.copy(alpha = 0.20f),
    )

private val LightFrostCardColors =
    FrostCardColors(
        container = Color.White.copy(alpha = 0.72f),
        border = FrostLightPrimary.copy(alpha = 0.18f),
    )

@Composable
fun InventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFrostCardColors provides if (darkTheme) DarkFrostCardColors else LightFrostCardColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = InventoryTypography,
            shapes = FrostShapes,
            content = content,
        )
    }
}
