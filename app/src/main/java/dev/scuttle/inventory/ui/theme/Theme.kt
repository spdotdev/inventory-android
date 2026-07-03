package dev.scuttle.inventory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = FrostAccent,
    onPrimary = FrostOnAccent,
    background = FrostDarkBackground,
    surface = FrostDarkSurface,
    surfaceVariant = FrostDarkSurfaceVariant,
    onSurface = FrostDarkOnSurface,
    onSurfaceVariant = FrostDarkOnSurfaceVariant,
    onBackground = FrostDarkOnSurface,
)

private val LightColors = lightColorScheme(
    primary = FrostLightPrimary,
    onPrimary = FrostLightOnPrimary,
    background = FrostLightBackground,
    surface = FrostLightSurface,
    surfaceVariant = FrostLightSurfaceVariant,
    onSurface = FrostLightOnSurface,
    onSurfaceVariant = FrostLightOnSurfaceVariant,
    onBackground = FrostLightOnSurface,
)

@Composable
fun InventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = InventoryTypography,
        shapes = FrostShapes,
        content = content,
    )
}
