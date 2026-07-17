package dev.scuttle.inventory.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.SnackbarErrorEffect
import dev.scuttle.inventory.ui.common.SortMenu
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.common.shelfDisplayName
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.theme.FrostCard

/**
 * The mandatory / out-of-stock filter chips and the sort menu.
 *
 * Extracted so it can be rendered on its own at a forced font scale — the sort label is the part
 * that breaks, and only at font scales the flow tests can't reach through a real Activity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductFilterSortRow(
    mandatoryOnly: Boolean,
    outOfStockOnly: Boolean,
    sort: SortOrder,
    onToggleMandatory: () -> Unit,
    onToggleOutOfStock: () -> Unit,
    onSortSelect: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    // FlowRow, not Row: the chips are sized to their content and are measured first, so in a Row
    // the sort label got only the leftover width — already too little at 360dp, and less again as
    // the font scales up. Here every item keeps its width and the row wraps instead.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = mandatoryOnly,
            onClick = onToggleMandatory,
            label = { Text(stringResource(R.string.products_pane_filter_mandatory)) },
        )
        FilterChip(
            selected = outOfStockOnly,
            onClick = onToggleOutOfStock,
            label = { Text(stringResource(R.string.products_pane_filter_out_of_stock)) },
        )
        SortMenu(current = sort, onSelect = onSortSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // One-shot scan outcome -> localized transient snackbar, then consumed.
    val scanResult = state.scanResult
    val incrementedText = stringResource(R.string.scan_incremented)
    val unknownText = stringResource(R.string.scan_unknown_code)
    LaunchedEffect(scanResult) {
        if (scanResult != null) {
            val message =
                when (scanResult) {
                    is ScanResult.Incremented -> incrementedText.format(scanResult.productName)
                    is ScanResult.Unknown -> unknownText.format(scanResult.code)
                }
            viewModel.consumeScanResult()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Undo snackbar for a product delete — the app's single most frequent
    // destructive action, and (before this) the only one with no Undo. A
    // snackbar with an action, rather than a one-shot error effect (which has
    // no action slot) — mirrors StorageOverviewScreen/ShelvesScreen.
    val undoLabel = stringResource(R.string.delete_undo)
    val productDeletedMessage = stringResource(R.string.product_deleted)
    LaunchedEffect(state.lastBatchId) {
        if (state.lastBatchId == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = productDeletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        } else {
            viewModel.consumeLastBatch()
        }
    }

    // The undo OUTCOME, as its own one-shot snackbar — distinct from the
    // "deleted, [Undo]" snackbar above. A 409 here (already restored
    // elsewhere, or past the undo window) shows the specific message instead
    // of falling through to a generic error.
    val undoneMessage = stringResource(R.string.delete_undone)
    val undoFailedMessage = stringResource(R.string.delete_undo_failed)
    LaunchedEffect(state.undoResult) {
        val message =
            when (state.undoResult) {
                UndoOutcome.SUCCESS -> undoneMessage
                UndoOutcome.FAILURE -> undoFailedMessage
                null -> return@LaunchedEffect
            }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeUndoResult()
    }

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
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Mutation failures (add/remove/move/delete) are shown via the Scaffold's
        // Snackbar (SnackbarErrorEffect above), which TalkBack announces. A LOAD
        // failure is different: a missed/dismissed snackbar there would otherwise
        // leave a blank screen with zero explanation (M4), so it gets its own
        // persistent inline ErrorRetry instead.
        state.loadError?.let { ErrorRetry(it, onRetry = viewModel::refresh) }

        if (state.products.isNotEmpty()) {
            OutlinedTextField(
                value = state.filterQuery,
                onValueChange = viewModel::onFilterQueryChange,
                label = { Text(stringResource(R.string.products_pane_filter_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("product-filter"),
            )
            ProductFilterSortRow(
                mandatoryOnly = state.mandatoryOnly,
                outOfStockOnly = state.outOfStockOnly,
                sort = state.sort,
                onToggleMandatory = viewModel::toggleMandatoryOnly,
                onToggleOutOfStock = viewModel::toggleOutOfStockOnly,
                onSortSelect = viewModel::setSort,
            )
        }

        if (state.products.isEmpty() && !state.loading && state.loadError == null) {
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

                val swipeState =
                    rememberSwipeToDismissBoxState(
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
                        // Only draw the delete affordance mid-swipe: the frosted (translucent)
                        // cards otherwise let the red container + bin icon bleed through at
                        // rest, overlapping the row's own trailing controls.
                        if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            Box(
                                modifier =
                                    Modifier
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
                        }
                    },
                ) {
                    val isMandatoryWarning = product.is_mandatory == true && product.quantity == 0
                    FrostCard(
                        onClick = { onOpenProduct(product) },
                        modifier = Modifier.fillMaxWidth().testTag("product-${product.id}"),
                    ) {
                        // Name on its own line, controls beneath it. Sharing one line with the
                        // stepper and the move button left the name only the width those
                        // controls didn't want — a quarter of the card, and less still in
                        // locales where "Move" is a longer word ("Verplaatsen"), which squashed
                        // names down to two or three characters (#31).
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isMandatoryWarning) {
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!product.code.isNullOrBlank()) {
                                Text(
                                    text = product.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (product.is_mandatory == true) {
                                Text(
                                    text = stringResource(R.string.products_pane_mandatory_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (isMandatoryWarning) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                )
                            }
                            // FlowRow, not Row: at large font scales the move button drops to
                            // its own line instead of being clipped off the card's edge.
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.decrement(product.id) },
                                        enabled = !state.loading && product.quantity > 0,
                                        modifier =
                                            Modifier.semantics {
                                                contentDescription = decreaseDesc
                                            },
                                    ) {
                                        Text("−")
                                    }
                                    Text(
                                        text = product.quantity.toString(),
                                        color =
                                            if (isMandatoryWarning) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.increment(product.id) },
                                        enabled = !state.loading,
                                        modifier =
                                            Modifier.semantics {
                                                contentDescription = increaseDesc
                                            },
                                    ) {
                                        Text("+")
                                    }
                                }
                                TextButton(
                                    onClick = { viewModel.startMove(product.id) },
                                    enabled = !state.loading,
                                    modifier =
                                        Modifier.semantics {
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
        }

        if (state.products.isNotEmpty()) {
            Text(
                text = stringResource(R.string.products_pane_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        // Tall enough that the stacked scan + add FABs never cover the last row.
        Spacer(Modifier.height(150.dp))
    }

    pendingDeleteId?.let { id ->
        val name = state.products.find { it.id == id }?.name ?: "this product"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.delete_dialog_product_title, name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
                    pendingDeleteId = null
                }) {
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
                        else ->
                            state.moveTargets.forEach { target ->
                                TextButton(onClick = { viewModel.confirmMove(target.shelfId) }) {
                                    Text(
                                        "${target.locationName} › " +
                                            shelfDisplayName(target.shelfName, target.isSystemShelf),
                                    )
                                }
                            }
                    }
                }
            },
        )
    }
}
