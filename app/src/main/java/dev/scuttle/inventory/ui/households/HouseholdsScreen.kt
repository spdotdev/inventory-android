package dev.scuttle.inventory.ui.households

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

        state.error?.let { Text(text = it) }

        if (state.households.isEmpty() && !state.loading) {
            Text(text = "No households yet. Create one or join with a code.")
        }

        state.households.forEach { household ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHousehold(household.id) },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = household.name)
                    Text(text = "Code: ${household.join_code}")
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
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::create, enabled = !state.loading) {
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
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::join, enabled = !state.loading) {
                Text("Join")
            }
        }
    }
}
