package dev.scuttle.inventory.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Deterministic per-household accent + icon layered on top of Frost. Purely
 * visual and client-side — nothing is persisted or sent to the API (consistent
 * with the server-authoritative rule); it just gives each household a stable,
 * distinguishable identity in the UI. The palette is a set of ice-toned hues
 * that all sit harmoniously against the Frost background in both light and dark.
 *
 * (User-chosen colors/icons would need a `theme` field on the household resource
 * — a Phase-2 backend change; this derives from the id so it works today.)
 */
data class HouseholdTheme(
    val accent: Color,
    val icon: ImageVector,
)

private val householdAccents = listOf(
    Color(0xFF7DD3FC), // sky (Frost accent)
    Color(0xFF5EEAD4), // teal
    Color(0xFFA5B4FC), // indigo
    Color(0xFFF9A8D4), // pink
    Color(0xFFFCD34D), // amber
    Color(0xFF86EFAC), // green
    Color(0xFFC4B5FD), // violet
    Color(0xFFFDBA74), // orange
)

private val householdIcons = listOf(
    Icons.Filled.Home,
    Icons.Filled.Kitchen,
    Icons.Filled.House,
    Icons.Filled.Apartment,
    Icons.Filled.Cottage,
    Icons.Filled.Warehouse,
    Icons.Filled.Storefront,
    Icons.Filled.Inventory2,
)

/** Stable theme for a household id. Index math lives in HouseholdThemeIndex.kt. */
fun householdTheme(id: Long): HouseholdTheme =
    HouseholdTheme(householdAccents[householdAccentIndex(id)], householdIcons[householdIconIndex(id)])

/**
 * Round, accent-tinted badge with the household's icon. The hue distinguishes
 * households; the icon is tinted with `onSurface` so it stays legible on the
 * translucent wash in either theme.
 */
@Composable
fun HouseholdAvatar(
    householdId: Long,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val theme = householdTheme(householdId)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(theme.accent.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = theme.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
