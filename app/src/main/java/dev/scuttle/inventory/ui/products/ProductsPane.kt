package dev.scuttle.inventory.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.ui.common.SnackbarErrorEffect
import dev.scuttle.inventory.ui.common.SortMenu
import dev.scuttle.inventory.ui.theme.FrostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsPane(
    householdId: Long,
    shelfId: Long,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    onOpenProduct: (ProductDto) -> Unit = {},
    onWarningChange: (Boolean) -> Unit = {},
    viewModel: ProductsViewModel = hiltViewModel(key = "products-$shelfId"),
) {
    val state by viewModel.state.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    // One-shot action errors (add/remove/move/delete) surface as a transient Snackbar
    // hosted by LocationDetailScreen's Scaffold, then are consumed so they don't re-fire
    // or re-announce. Material3's Snackbar is itself a live region (a11y).
    SnackbarErrorEffect(state.error, snackbarHostState, onConsumed = viewModel::consumeError)

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, householdId, shelfId, refreshKey) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.load(householdId, shelfId)
        }
    }

    // Only report warning when it actually changes — avoids HierarchyStore state thrashing
    var lastWarning by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.products) {
        val hasWarning = state.products.any { it.is_mandatory == true && it.quantity == 0 }
        if (hasWarning != lastWarning) {
            lastWarning = hasWarning
            onWarningChange(hasWarning)
        }
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

        // Errors are shown via the Scaffold's Snackbar (SnackbarErrorEffect above),
        // which TalkBack announces — no sticky, silent inline error text.

        if (state.products.isNotEmpty()) {
            OutlinedTextField(
                value = state.filterQuery,
                onValueChange = viewModel::onFilterQueryChange,
                label = { Text(stringResource(R.string.products_pane_filter_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("product-filter"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.mandatoryOnly,
                    onClick = viewModel::toggleMandatoryOnly,
                    label = { Text(stringResource(R.string.products_pane_filter_mandatory)) },
                )
                FilterChip(
                    selected = state.outOfStockOnly,
                    onClick = viewModel::toggleOutOfStockOnly,
                    label = { Text(stringResource(R.string.products_pane_filter_out_of_stock)) },
                )
                Spacer(Modifier.weight(1f))
                SortMenu(current = state.sort, onSelect = viewModel::setSort)
            }
        }

        if (state.products.isEmpty() && !state.loading) {
            Text(text = stringResource(R.string.products_pane_empty))
        } else if (state.filteredToEmpty) {
            Text(text = stringResource(R.string.products_pane_no_match))
        }

        state.visibleProducts.forEach { product ->
            key(product.id) {
                // Hoist formatted strings so they can be used inside non-composable semantics blocks
                val decreaseDesc = stringResource(R.string.products_pane_decrease_cd, product.name)
                val increaseDesc = stringResource(R.string.products_pane_increase_cd, product.name)
                val moveDesc = stringResource(R.string.products_pane_move_cd, product.name)

                val swipeState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            pendingDeleteId = product.id
                        }
                        false
                    },
                )
                SwipeToDismissBox(
                    state = swipeState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    },
                ) {
                    val isMandatoryWarning = product.is_mandatory == true && product.quantity == 0
                    FrostCard(
                        onClick = { onOpenProduct(product) },
                        modifier = Modifier.fillMaxWidth().testTag("product-${product.id}"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isMandatoryWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(text = product.name, style = MaterialTheme.typography.bodyLarge)
                                    if (!product.code.isNullOrBlank()) {
                                        Text(
                                            text = product.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (product.is_mandatory == true) {
                                    Text(
                                        text = stringResource(R.string.products_pane_mandatory_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMandatoryWarning) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.decrement(product.id) },
                                enabled = !state.loading && product.quantity > 0,
                                modifier = Modifier.semantics {
                                    contentDescription = decreaseDesc
                                },
                            ) {
                                Text("−")
                            }
                            Text(
                                text = product.quantity.toString(),
                                color = if (isMandatoryWarning) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                            OutlinedButton(
                                onClick = { viewModel.increment(product.id) },
                                enabled = !state.loading,
                                modifier = Modifier.semantics {
                                    contentDescription = increaseDesc
                                },
                            ) {
                                Text("+")
                            }
                            TextButton(
                                onClick = { viewModel.startMove(product.id) },
                                enabled = !state.loading,
                                modifier = Modifier.semantics {
                                    contentDescription = moveDesc
                                },
                            ) {
                                Text(stringResource(R.string.products_pane_move_button))
                            }
                        }
                    }
                }
            }
        }

        if (state.products.isNotEmpty()) {
            Text(
                text = stringResource(R.string.products_pane_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    pendingDeleteId?.let { id ->
        val name = state.products.find { it.id == id }?.name ?: "this product"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.delete_dialog_product_title, name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); pendingDeleteId = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.movingProductId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelMove,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelMove) { Text(stringResource(R.string.action_cancel)) }
            },
            title = { Text(stringResource(R.string.products_pane_move_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        state.loading && state.moveTargets.isEmpty() ->
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        state.moveTargets.isEmpty() ->
                            Text(stringResource(R.string.products_pane_no_shelves_to_move))
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
