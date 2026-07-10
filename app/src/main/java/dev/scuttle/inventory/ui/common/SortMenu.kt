package dev.scuttle.inventory.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R

/**
 * Compact "Sort: <current>" button that opens a dropdown of the [SortOrder]
 * options with a check beside the active one. Shared by Products and Search.
 */
@Composable
fun SortMenu(
    current: SortOrder,
    onSelect: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("sort-menu")) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null)
            Text(
                text = stringResource(R.string.sort_label, stringResource(current.labelRes)),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes)) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    },
                    trailingIcon =
                        if (order == current) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}
