package dev.scuttle.inventory.ui.hierarchy

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
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.theme.FrostCard

/**
 * One row of the shelves list view.
 *
 * Outside edit mode this is a plain [FrostCard]: tapping it opens the shelf, same
 * as tapping its tab did before. In edit mode the row grows a leading [Checkbox]
 * (selection for the batch delete/confirm flow), a rename pencil, and trailing
 * up/down [IconButton]s for the manual drag order — all three gated off for the
 * Unsorted shelf via [isSystem], since it can't be renamed, selected, or reordered.
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
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp),
            )
            if (editable) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.shelf_rename_title))
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.shelf_move_up_cd),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.shelf_move_down_cd),
                    )
                }
            }
        }
    }
}
