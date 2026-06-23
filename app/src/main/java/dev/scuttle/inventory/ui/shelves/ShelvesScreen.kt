package dev.scuttle.inventory.ui.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ShelvesScreen(
    householdId: Long,
    locationId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ShelvesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(householdId, locationId) {
        viewModel.load(householdId, locationId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("← Storage")
        }

        Text(text = "Shelves")

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { Text(text = it) }

        if (state.shelves.isEmpty() && !state.loading) {
            Text(text = "No shelves yet. Add one below.")
        }

        state.shelves.forEach { shelf ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = shelf.name, modifier = Modifier.padding(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.newName,
                onValueChange = viewModel::onNewNameChange,
                label = { Text("New shelf") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::create, enabled = !state.loading) {
                Text("Add")
            }
        }
    }
}
