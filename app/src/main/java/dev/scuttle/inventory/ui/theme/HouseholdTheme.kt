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

/**
 * Keyed palette shared with the server (HouseholdColor/HouseholdIcon enums in
 * inventory-laravel): the API stores KEYS, each client renders them. Order
 * doubles as the derived-fallback palette, so don't reorder.
 */
val householdAccentsByKey =
    linkedMapOf(
        "sky" to Color(0xFF7DD3FC), // Frost accent
        "teal" to Color(0xFF5EEAD4),
        "indigo" to Color(0xFFA5B4FC),
        "pink" to Color(0xFFF9A8D4),
        "amber" to Color(0xFFFCD34D),
        "green" to Color(0xFF86EFAC),
        "violet" to Color(0xFFC4B5FD),
        "orange" to Color(0xFFFDBA74),
    )

val householdIconsByKey =
    linkedMapOf(
        "home" to Icons.Filled.Home,
        "kitchen" to Icons.Filled.Kitchen,
        "house" to Icons.Filled.House,
        "apartment" to Icons.Filled.Apartment,
        "cottage" to Icons.Filled.Cottage,
        "warehouse" to Icons.Filled.Warehouse,
        "storefront" to Icons.Filled.Storefront,
        "box" to Icons.Filled.Inventory2,
    )

private val householdAccents = householdAccentsByKey.values.toList()
private val householdIcons = householdIconsByKey.values.toList()

/**
 * Theme for a household: user-chosen keys win; a null (or unknown, e.g. a key
 * added server-side after this build shipped) key falls back to the stable
 * id-derived default. Index math lives in HouseholdThemeIndex.kt.
 */
fun householdTheme(
    id: Long,
    colorKey: String? = null,
    iconKey: String? = null,
): HouseholdTheme =
    HouseholdTheme(
        accent = householdAccentsByKey[colorKey] ?: householdAccents[householdAccentIndex(id)],
        icon = householdIconsByKey[iconKey] ?: householdIcons[householdIconIndex(id)],
    )

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
    colorKey: String? = null,
    iconKey: String? = null,
) {
    val theme = householdTheme(householdId, colorKey, iconKey)
    Box(
        modifier =
            modifier
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
