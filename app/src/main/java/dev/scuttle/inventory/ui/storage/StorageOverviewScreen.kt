package dev.scuttle.inventory.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageOverviewScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenLocation: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenInvite: () -> Unit = {},
    viewModel: StorageOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(householdId) {
        viewModel.load(householdId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("← Households")
        }

        Text(text = "Storage")

        TextButton(onClick = onOpenSearch) {
            Text("Search products")
        }

        TextButton(onClick = onOpenInvite) {
            Text("Invite members")
        }

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        if (state.locations.isEmpty() && !state.loading) {
            Text(text = "No storage locations yet. Add a freezer, fridge or pantry below.")
        }

        state.locations.forEach { location ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Open ${location.name}" }
                    .clickable { onOpenLocation(location.id) },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = location.name)
                    Text(text = location.type)
                }
            }
        }

        Text(text = "Add storage")

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STORAGE_TYPES.forEach { type ->
                FilterChip(
                    selected = state.newType == type,
                    onClick = { viewModel.onTypeSelect(type) },
                    label = { Text(type) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.newName,
                onValueChange = viewModel::onNewNameChange,
                label = { Text("Name") },
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
                Text("Add")
            }
        }
    }
}
