package dev.scuttle.inventory.ui.hierarchy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.theme.FrostCard

/**
 * One row of an editable hierarchy list — shared by the shelves list
 * (LocationDetailScreen) and the locations list (StorageOverviewScreen).
 *
 * Outside edit mode this is a plain [FrostCard]: tapping it opens the row's
 * target, same as before edit mode existed. In edit mode the row grows a
 * leading [Checkbox] (selection for the batch delete/confirm flow), a rename
 * pencil, and trailing up/down [IconButton]s for the manual drag order — all
 * three gated off via [isSystem] for a row that can't be renamed, selected, or
 * reordered (the shelves screen's "Unsorted" shelf; locations have no
 * equivalent and always pass `isSystem = false`).
 */
@Composable
fun EditableRow(
    name: String,
    editMode: Boolean,
    isSystem: Boolean,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    // Selecting (the checkbox) never hits the network, so it stays enabled while a
    // mutation is in flight — only rename/reorder, which each fire their own
    // request immediately, need to be held off to avoid racing a second one.
    actionsEnabled: Boolean = true,
    // Defaults preserve the shelves-screen wording; a caller editing a different
    // kind of row (e.g. locations) passes its own copy so a screen reader never
    // announces "Rename shelf" over a location row.
    @StringRes renameLabelRes: Int = R.string.shelf_rename_title,
    @StringRes moveUpLabelRes: Int = R.string.shelf_move_up_cd,
    @StringRes moveDownLabelRes: Int = R.string.shelf_move_down_cd,
) {
    val editable = editMode && !isSystem
    FrostCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editable) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp),
            )
            if (editable) {
                IconButton(onClick = onRename, enabled = actionsEnabled) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(renameLabelRes))
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp && actionsEnabled) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(moveUpLabelRes),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown && actionsEnabled) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(moveDownLabelRes),
                    )
                }
            }
        }
    }
}
