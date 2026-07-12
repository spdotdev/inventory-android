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
 *
 * These are the pastels used for the avatar wash. For a solid fill (the dashboard
 * bars) use [householdBarAccent] instead — see HouseholdPalette.kt for why.
 */
val householdAccentsByKey = householdAccentDarkArgb.mapValuesTo(linkedMapOf()) { Color(it.value) }

/** Light-theme counterparts, dark enough to survive as a solid bar. */
private val householdAccentsLightByKey = householdAccentLightArgb.mapValuesTo(linkedMapOf()) { Color(it.value) }

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
 * The household's accent as a **solid fill** (dashboard bars), picked for the
 * active theme so it keeps >= 3:1 against the surfaceVariant track it sits on.
 * The pastel in [HouseholdTheme.accent] is for the translucent avatar wash and is
 * not readable as a light-theme bar — see HouseholdPalette.kt.
 */
@Composable
fun householdBarAccent(
    id: Long,
    colorKey: String? = null,
): Color {
    val palette = if (LocalFrostIsDark.current) householdAccentsByKey else householdAccentsLightByKey
    return palette[colorKey] ?: palette.values.toList()[householdAccentIndex(id)]
}

/**
 * Round, accent-tinted badge with the household's icon. The hue distinguishes
 * households; the icon is tinted with `onSurface` so it stays legible on the
 * translucent wash in either theme.
 *
 * Pass [contentDescription] (the household name) wherever the avatar is the only
 * thing saying which household a row belongs to — on the dashboard it carries
 * that meaning, so it must not be silent to TalkBack.
 */
@Composable
fun HouseholdAvatar(
    householdId: Long,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    colorKey: String? = null,
    iconKey: String? = null,
    contentDescription: String? = null,
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
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
