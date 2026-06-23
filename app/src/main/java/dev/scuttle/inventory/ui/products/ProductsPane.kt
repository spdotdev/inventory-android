package dev.scuttle.inventory.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * The products for a single shelf, rendered as one page of the location's pager.
 * Keyed ViewModel so each shelf page keeps its own independent state.
 */
@Composable
fun ProductsPane(
    householdId: Long,
    shelfId: Long,
    modifier: Modifier = Modifier,
    viewModel: ProductsViewModel = hiltViewModel(key = "products-$shelfId"),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(householdId, shelfId) {
        viewModel.load(householdId, shelfId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { Text(text = it) }

        if (state.products.isEmpty() && !state.loading) {
            Text(text = "No products on this shelf yet. Add one below.")
        }

        state.products.forEach { product ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = product.name, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { viewModel.decrement(product.id) }, enabled = !state.loading) {
                        Text("−")
                    }
                    Text(text = product.quantity.toString())
                    OutlinedButton(onClick = { viewModel.increment(product.id) }, enabled = !state.loading) {
                        Text("+")
                    }
                    TextButton(onClick = { viewModel.startMove(product.id) }, enabled = !state.loading) {
                        Text("Move")
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
                label = { Text("New product") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::create, enabled = !state.loading) {
                Text("Add")
            }
        }
    }

    if (state.movingProductId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelMove,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelMove) { Text("Cancel") }
            },
            title = { Text("Move to…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.moveTargets.isEmpty()) {
                        Text("No other shelves available.")
                    }
                    state.moveTargets.forEach { target ->
                        TextButton(onClick = { viewModel.confirmMove(target.shelfId) }) {
                            Text(target.label)
                        }
                    }
                }
            },
        )
    }
}
