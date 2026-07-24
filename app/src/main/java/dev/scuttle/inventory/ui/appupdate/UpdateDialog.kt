package dev.scuttle.inventory.ui.appupdate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.appupdate.UpdateStatus

@Composable
fun UpdateDialog(
    status: UpdateStatus,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release =
        when (status) {
            is UpdateStatus.Optional -> status.release
            is UpdateStatus.Breaking -> status.release
            UpdateStatus.None -> return
        }
    val isBreaking = status is UpdateStatus.Breaking

    AlertDialog(
        onDismissRequest = { if (!isBreaking) onDismiss() },
        properties =
            DialogProperties(
                dismissOnBackPress = !isBreaking,
                dismissOnClickOutside = !isBreaking,
            ),
        title = {
            Text(
                stringResource(
                    id =
                        if (isBreaking) {
                            R.string.update_dialog_required_title
                        } else {
                            R.string.update_dialog_available_title
                        },
                ),
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Text(release.versionName)
                Text(release.changelog)
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdateClick) {
                Text(stringResource(id = R.string.update_dialog_update_now))
            }
        },
        dismissButton =
            if (!isBreaking) {
                {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.update_dialog_later))
                    }
                }
            } else {
                null
            },
    )
}
