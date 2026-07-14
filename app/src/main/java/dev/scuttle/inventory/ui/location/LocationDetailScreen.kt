package dev.scuttle.inventory.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.common.LiveStatusText
import dev.scuttle.inventory.ui.common.shelfDisplayName
import dev.scuttle.inventory.ui.hierarchy.DeleteStrategyDialog
import dev.scuttle.inventory.ui.hierarchy.EditableRow
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.hierarchy.shelfStrategyOptions
import dev.scuttle.inventory.ui.products.ProductsPane
import dev.scuttle.inventory.ui.products.ProductsViewModel
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Tab as TabViewIcon

/** Matches the server-side shelf name column limit (same cap as ShelvesViewModel.onNewNameChange). */
private const val MAX_SHELF_NAME_LENGTH = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    householdId: Long,
    locationId: Long,
    drawerViewModel: DrawerViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    onOpenScanner: () -> Unit = {},
    scannedCode: String? = null,
    onScannedCodeConsumed: () -> Unit = {},
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
    val state by shelvesViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { state.shelves.size })
    val currentPage = pagerState.currentPage.coerceAtMost((state.shelves.size - 1).coerceAtLeast(0))
    val currentShelfId = state.shelves.getOrNull(currentPage)?.id
    val nonSystemShelfCount = state.shelves.count { !it.is_system }

    // The location's own name for the top-bar title (ALSO FIX, final review): this
    // screen used to render the generic "Shelves" title always, even though the
    // location itself became renamable this branch. drawerViewModel already holds
    // every household's locations (it's what got the user here in the first
    // place), so no extra network call is needed — just fall back to the generic
    // title for the brief window before that data has loaded.
    val drawerState by drawerViewModel.state.collectAsState()
    val locationName =
        drawerState.entries
            .firstOrNull { it.id == householdId }
            ?.locations
            ?.firstOrNull { it.id == locationId }
            ?.name

    var showAddShelfSheet by rememberSaveable { mutableStateOf(false) }
    var showAddProductSheet by rememberSaveable { mutableStateOf(false) }
    var productsRefreshKey by remember { mutableIntStateOf(0) }
    var renamingShelf by remember { mutableStateOf<ShelfDto?>(null) }
    var renameText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track per-shelf warning state so we can roll up to location level
    var shelfWarnings by remember { mutableStateOf(mapOf<Long, Boolean>()) }

    LaunchedEffect(householdId, locationId) {
        shelvesViewModel.load(householdId, locationId)
    }

    // Hosts one-shot action errors from the ProductsPane(s) below, plus the
    // delete-undo snackbar (LaunchedEffect further down).
    val snackbarHostState = remember { SnackbarHostState() }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    if (state.editMode && state.selected.isNotEmpty()) {
                        Text(stringResource(R.string.location_selected_count, state.selected.size))
                    } else {
                        Text(locationName ?: stringResource(R.string.location_shelves_title))
                    }
                },
                navigationIcon = {
                    if (state.editMode) {
                        TextButton(
                            onClick = shelvesViewModel::exitEditMode,
                        ) { Text(stringResource(R.string.action_cancel)) }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (state.editMode) {
                        Button(
                            // requestDelete() only OPENS the strategy dialog — the actual
                            // delete happens in confirmDelete(), wired to the dialog below.
                            // This button must never call confirmDelete() directly: that
                            // would be exactly the no-confirmation bug this task replaces.
                            onClick = shelvesViewModel::requestDelete,
                            enabled = state.selected.isNotEmpty() && !state.loading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                if (state.selected.isEmpty()) {
                                    stringResource(R.string.location_delete_button)
                                } else {
                                    stringResource(R.string.location_delete_count_button, state.selected.size)
                                },
                            )
                        }
                    } else {
                        IconButton(onClick = shelvesViewModel::toggleListView) {
                            Icon(
                                imageVector = if (state.listView) Icons.Default.TabViewIcon else Icons.Default.ViewList,
                                contentDescription = stringResource(R.string.location_view_toggle_cd),
                            )
                        }
                        if (state.shelves.isNotEmpty()) {
                            IconButton(onClick = shelvesViewModel::enterEditMode) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.location_edit_shelves_cd),
                                )
                            }
                        }
                        IconButton(onClick = { showAddShelfSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.location_add_shelf_cd))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.editMode && currentShelfId != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = onOpenScanner) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.location_scan_cd),
                        )
                    }
                    FloatingActionButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = { showAddProductSheet = true },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.location_add_product_cd))
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = {
                shelvesViewModel.refresh()
                productsRefreshKey++
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
            ) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.error?.let {
                    LiveStatusText(it, modifier = Modifier.padding(16.dp))
                }

                if (state.shelves.isEmpty()) {
                    // Suppress "no shelves yet" on a failed load — the error line above
                    // already explains it; showing both reads as a false empty (W7).
                    if (!state.loading && state.error == null) {
                        Text(
                            text = stringResource(R.string.location_no_shelves),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (state.listView) {
                    // Edit mode always renders here (enterEditMode() forces listView),
                    // since a ScrollableTabRow cannot host reorder buttons or an inline
                    // rename target. Outside edit mode this is just an alternative,
                    // full-name shelf selector — tapping a row drills back into the
                    // tabs+pager view centered on that shelf.
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.shelves, key = { _, shelf -> shelf.id }) { index, shelf ->
                            EditableRow(
                                name = shelfDisplayName(shelf),
                                editMode = state.editMode,
                                isSystem = shelf.is_system,
                                selected = shelf.id in state.selected,
                                canMoveUp = !shelf.is_system && index > 0,
                                canMoveDown = !shelf.is_system && index < nonSystemShelfCount - 1,
                                // Selecting doesn't touch the network, but rename/reorder each
                                // fire their own request immediately — held off while another
                                // mutation is in flight so two don't race each other.
                                actionsEnabled = !state.loading,
                                onClick = {
                                    if (state.editMode) {
                                        shelvesViewModel.toggleSelection(shelf.id)
                                    } else {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        shelvesViewModel.toggleListView()
                                    }
                                },
                                onRename = {
                                    renamingShelf = shelf
                                    renameText = shelf.name
                                },
                                onMoveUp = { shelvesViewModel.moveUp(shelf.id) },
                                onMoveDown = { shelvesViewModel.moveDown(shelf.id) },
                            )
                        }
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = currentPage,
                    ) {
                        state.shelves.forEachIndexed { index, shelf ->
                            val tabHasWarning = shelfWarnings[shelf.id] == true
                            // The warning is conveyed visually by red text + a dot; give the
                            // text row a content description so it isn't color-only (WCAG
                            // 1.4.1) and TalkBack announces "<shelf>, has missing items" (W9).
                            val warningCd =
                                if (tabHasWarning) {
                                    stringResource(R.string.location_shelf_missing_cd, shelfDisplayName(shelf))
                                } else {
                                    null
                                }
                            Tab(
                                selected = currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier =
                                            if (warningCd != null) {
                                                Modifier.clearAndSetSemantics { contentDescription = warningCd }
                                            } else {
                                                Modifier
                                            },
                                    ) {
                                        Text(
                                            shelfDisplayName(shelf),
                                            color =
                                                if (tabHasWarning) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    Color.Unspecified
                                                },
                                        )
                                        if (tabHasWarning) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(6.dp)
                                                        .background(MaterialTheme.colorScheme.error, CircleShape),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) { page ->
                        val shelf = state.shelves[page]
                        ProductsPane(
                            householdId = householdId,
                            shelfId = shelf.id,
                            snackbarHostState = snackbarHostState,
                            onOpenProduct = { product -> onOpenProduct(householdId, product.shelf_id, product.id) },
                            onWarningChange = { hasWarning ->
                                shelfWarnings = shelfWarnings + (shelf.id to hasWarning)
                                drawerViewModel.reportLocationWarning(locationId, shelfWarnings.values.any { it })
                            },
                            refreshKey = productsRefreshKey,
                        )
                    }
                }
            }
        } // end PullToRefreshBox
    }

    // The delete-strategy dialog for the current selection. Non-null pendingDelete
    // is the ONLY thing that shows this dialog, and confirmDelete() (the only path
    // that actually deletes) is reachable exclusively from its confirm button — the
    // top bar's Delete button above only ever calls requestDelete().
    state.pendingDelete?.let { plan ->
        DeleteStrategyDialog(
            plan = plan,
            options = shelfStrategyOptions(),
            targets = state.moveTargets,
            onDismiss = shelvesViewModel::cancelDelete,
            onConfirm = { strategy, targetId -> shelvesViewModel.confirmDelete(strategy, targetId) },
        )
    }

    // Undo snackbar. A snackbar with an action, rather than SnackbarErrorEffect
    // (which is for one-shot errors and has no action slot).
    val undoLabel = stringResource(R.string.delete_undo)
    val deletedMessage = stringResource(R.string.shelves_deleted)
    LaunchedEffect(state.lastBatchId) {
        if (state.lastBatchId == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            shelvesViewModel.undoDelete()
        } else {
            shelvesViewModel.consumeLastBatch()
        }
    }

    // The undo OUTCOME, as its own one-shot snackbar — distinct from the "deleted,
    // [Undo]" snackbar above. A 409 here (already restored elsewhere, or past the
    // undo window) used to fall through to a generic error; this shows the specific
    // message instead (final review, ALSO FIX).
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
        shelvesViewModel.consumeUndoResult()
    }

    renamingShelf?.let { shelf ->
        AlertDialog(
            onDismissRequest = { renamingShelf = null },
            title = { Text(stringResource(R.string.shelf_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(MAX_SHELF_NAME_LENGTH) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        shelvesViewModel.rename(shelf.id, renameText)
                        renamingShelf = null
                    },
                    enabled = renameText.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renamingShelf = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showAddShelfSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddShelfSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.add_shelf_sheet_title), style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = shelvesViewModel::onNewNameChange,
                        label = { Text(stringResource(R.string.add_shelf_field_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                keyboardController?.hide()
                                shelvesViewModel.create()
                                showAddShelfSheet = false
                            }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            shelvesViewModel.create()
                            showAddShelfSheet = false
                        },
                        enabled = !state.loading && state.newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
        }
    }

    if (scannedCode != null && currentShelfId != null) {
        val activePaneViewModel: ProductsViewModel = hiltViewModel(key = "products-$currentShelfId")
        LaunchedEffect(scannedCode) {
            activePaneViewModel.onBarcodeScanned(scannedCode)
            onScannedCodeConsumed()
        }
    }

    if (showAddProductSheet && currentShelfId != null) {
        AddProductSheet(
            householdId = householdId,
            shelfId = currentShelfId,
            onDismiss = { showAddProductSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProductSheet(
    householdId: Long,
    shelfId: Long,
    onDismiss: () -> Unit,
    viewModel: ProductsViewModel = hiltViewModel(key = "products-$shelfId"),
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_product_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = viewModel::onNewNameChange,
                        label = { Text(stringResource(R.string.add_product_field_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                keyboardController?.hide()
                                viewModel.create()
                                onDismiss()
                            }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.create()
                            onDismiss()
                        },
                        enabled = !state.loading && state.newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                state.suggestions.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    viewModel.selectSuggestion(name)
                                }.padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
