package dev.scuttle.inventory.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.ui.theme.householdAccentsByKey
import dev.scuttle.inventory.ui.theme.householdIconsByKey
import dev.scuttle.inventory.ui.theme.themeFor

private val SWATCH_SIZE = 40.dp
private val SWATCH_ICON_SIZE = 22.dp
private const val SWATCH_BACKGROUND_ALPHA = 0.28f

/**
 * Colour/icon swatch pickers shared by every full-page theme editor
 * (HouseholdEditScreen, ShelfEditScreen) — extracted out of HouseholdEditScreen
 * (where they first shipped) so a second themeable entity reuses the exact same
 * composables rather than a copy-pasted FlowRow. The keyed palette itself
 * (`householdAccentsByKey`/`householdIconsByKey`) is shared with the server
 * (HouseholdColor/HouseholdIcon enums in inventory-laravel) and reused verbatim
 * for shelves — same enum keys, per the shelf theming backend contract.
 *
 * `testTag`s ("theme-color-<key>" / "theme-icon-<key>") are preserved exactly
 * as HouseholdEditScreenTest already asserts them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchPicker(
    selectedColor: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        householdAccentsByKey.forEach { (key, accent) ->
            Box(
                modifier =
                    Modifier
                        .size(SWATCH_SIZE)
                        .clip(CircleShape)
                        .background(accent)
                        .selectionBorder(selected = selectedColor == key)
                        .clickable { onSelect(key) }
                        .testTag("theme-color-$key"),
            )
        }
    }
}

/**
 * [id] is the themeable entity's own stable id (household id or shelf id) —
 * only used to derive the icon-swatch background wash via [themeFor] when no
 * colour is chosen yet, exactly as HouseholdEditScreen's icon picker already did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconSwatchPicker(
    id: Long,
    selectedColor: String?,
    selectedIcon: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconBackground = themeFor(id, selectedColor).accent.copy(alpha = SWATCH_BACKGROUND_ALPHA)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        householdIconsByKey.forEach { (key, image) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(SWATCH_SIZE)
                        .clip(CircleShape)
                        .background(iconBackground)
                        .selectionBorder(selected = selectedIcon == key)
                        .clickable { onSelect(key) }
                        .testTag("theme-icon-$key"),
            ) {
                Icon(
                    imageVector = image,
                    contentDescription = key,
                    modifier = Modifier.size(SWATCH_ICON_SIZE),
                )
            }
        }
    }
}

@Composable
fun Modifier.selectionBorder(selected: Boolean): Modifier =
    if (selected) {
        border(width = 3.dp, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
    } else {
        this
    }
