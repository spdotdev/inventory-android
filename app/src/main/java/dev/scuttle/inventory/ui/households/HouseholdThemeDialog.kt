package dev.scuttle.inventory.ui.households

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.ui.theme.householdAccentsByKey
import dev.scuttle.inventory.ui.theme.householdIconsByKey
import dev.scuttle.inventory.ui.theme.householdTheme

private val SWATCH_SIZE = 40.dp
private val SWATCH_ICON_SIZE = 22.dp
private const val SWATCH_BACKGROUND_ALPHA = 0.28f

/**
 * Color + icon picker for a household's theme. "Default" (null keys) restores
 * the stable id-derived look, so the selected state must distinguish "chose
 * the key that happens to equal the derived one" from "no choice".
 */
@Composable
fun HouseholdThemeDialog(
    household: HouseholdDto,
    onDismiss: () -> Unit,
    onSave: (color: String?, icon: String?) -> Unit,
) {
    var color by remember { mutableStateOf(household.color) }
    var icon by remember { mutableStateOf(household.icon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.household_theme_title, household.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.household_theme_color_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
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
                                    .selectionBorder(selected = color == key)
                                    .clickable { color = key }
                                    .testTag("theme-color-$key"),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.household_theme_icon_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val iconBackground =
                        householdTheme(household.id, color).accent.copy(alpha = SWATCH_BACKGROUND_ALPHA)
                    householdIconsByKey.forEach { (key, image) ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(SWATCH_SIZE)
                                    .clip(CircleShape)
                                    .background(iconBackground)
                                    .selectionBorder(selected = icon == key)
                                    .clickable { icon = key }
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
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(color, icon) },
                modifier = Modifier.testTag("theme-save"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            // "Default" clears both keys back to the derived look; Cancel keeps
            // whatever is stored.
            TextButton(onClick = { onSave(null, null) }) {
                Text(stringResource(R.string.household_theme_default))
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Modifier.selectionBorder(selected: Boolean): Modifier =
    if (selected) {
        border(width = 3.dp, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
    } else {
        this
    }
