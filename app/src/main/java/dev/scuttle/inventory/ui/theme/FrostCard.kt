package dev.scuttle.inventory.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Container + border tint for the Frost translucent card treatment (D-021), theme-aware. */
data class FrostCardColors(
    val container: Color,
    val border: Color,
)

val LocalFrostCardColors =
    staticCompositionLocalOf {
        FrostCardColors(container = Color.Unspecified, border = Color.Unspecified)
    }

private val FrostCardShape = RoundedCornerShape(22.dp)

/**
 * Frost's signature translucent card: low-opacity tinted background + hairline border on
 * generously rounded corners (frost-app.html `.card`/`.rcard`/`.fcard`/`.codecard`). Use this
 * instead of the default Material `Card` for list/info cards. Semantically-colored cards
 * (e.g. warning containers) pass [containerColor] to keep the Frost geometry (corner radius +
 * hairline border) while standing out by tint — a plain Material `Card` next to FrostCards
 * reads as a differently-sized row (smaller radius, no border), which is exactly the bug that
 * motivated this parameter (2026-07-26, storage list warning rows).
 */
@Composable
fun FrostCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalFrostCardColors.current
    val cardColors = CardDefaults.cardColors(containerColor = containerColor ?: colors.container)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val cardBorder = BorderStroke(1.dp, colors.border)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = FrostCardShape,
            colors = cardColors,
            elevation = cardElevation,
            border = cardBorder,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = FrostCardShape,
            colors = cardColors,
            elevation = cardElevation,
            border = cardBorder,
            content = content,
        )
    }
}
