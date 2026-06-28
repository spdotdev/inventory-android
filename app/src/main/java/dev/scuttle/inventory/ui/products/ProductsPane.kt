package dev.scuttle.inventory.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
    val keyboardController = LocalSoftwareKeyboardController.current

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

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

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
                    OutlinedButton(
                        onClick = { viewModel.decrement(product.id) },
                        enabled = !state.loading && product.quantity > 0,
                        modifier = Modifier.semantics {
                            contentDescription = "Decrease ${product.name} quantity"
                        },
                    ) {
                        Text("−")
                    }
                    Text(text = product.quantity.toString())
                    OutlinedButton(
                        onClick = { viewModel.increment(product.id) },
                        enabled = !state.loading,
                        modifier = Modifier.semantics {
                            contentDescription = "Increase ${product.name} quantity"
                        },
                    ) {
                        Text("+")
                    }
                    TextButton(
                        onClick = { viewModel.startMove(product.id) },
                        enabled = !state.loading,
                        modifier = Modifier.semantics {
                            contentDescription = "Move ${product.name}"
                        },
                    ) {
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
                    when {
                        state.loading && state.moveTargets.isEmpty() ->
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        state.moveTargets.isEmpty() ->
                            Text("No other shelves available.")
                        else -> state.moveTargets.forEach { target ->
                            TextButton(onClick = { viewModel.confirmMove(target.shelfId) }) {
                                Text(target.label)
                            }
                        }
                    }
                }
            },
        )
    }
}
