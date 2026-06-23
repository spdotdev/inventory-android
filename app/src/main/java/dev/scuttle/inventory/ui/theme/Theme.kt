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
    onSurface = FrostDarkOnSurface,
    onBackground = FrostDarkOnSurface,
)

private val LightColors = lightColorScheme(
    primary = FrostAccent,
    onPrimary = FrostOnAccent,
    background = FrostLightBackground,
    surface = FrostLightSurface,
    onSurface = FrostLightOnSurface,
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
        content = content,
    )
}
