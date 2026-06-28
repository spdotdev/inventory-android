package dev.scuttle.inventory.ui.households

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HouseholdsScreen(
    modifier: Modifier = Modifier,
    onOpenHousehold: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var confirmLeaveId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onOpenSettings) {
            Text("Settings")
        }

        Text(text = "Your households")

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        if (state.households.isEmpty() && !state.loading) {
            Text(text = "No households yet. Create one or join with a code.")
        }

        state.households.forEach { household ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Open ${household.name}" }
                    .clickable { onOpenHousehold(household.id) },
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(text = household.name)
                        Text(text = "Code: ${household.join_code}")
                    }
                    TextButton(onClick = { confirmLeaveId = household.id }) {
                        Text("Leave", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.newName,
                onValueChange = viewModel::onNewNameChange,
                label = { Text("New household") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide(); viewModel.create() }
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { keyboardController?.hide(); viewModel.create() },
                enabled = !state.loading && state.newName.isNotBlank(),
            ) {
                Text("Create")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.joinCode,
                onValueChange = viewModel::onJoinCodeChange,
                label = { Text("Join code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide(); viewModel.join() }
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { keyboardController?.hide(); viewModel.join() },
                enabled = !state.loading && state.joinCode.isNotBlank(),
            ) {
                Text("Join")
            }
        }
    }

    confirmLeaveId?.let { id ->
        val name = state.households.find { it.id == id }?.name ?: "this household"
        AlertDialog(
            onDismissRequest = { confirmLeaveId = null },
            title = { Text("Leave $name?") },
            text = { Text("You'll need a new invite to rejoin.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.leave(id); confirmLeaveId = null },
                ) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeaveId = null }) { Text("Cancel") }
            },
        )
    }
}
