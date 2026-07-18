package dev.scuttle.inventory.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.storageTypeLabel
import dev.scuttle.inventory.ui.hierarchy.DeleteStrategyDialog
import dev.scuttle.inventory.ui.hierarchy.EditableRow
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.hierarchy.locationStrategyOptions
import dev.scuttle.inventory.ui.theme.FrostCard

/** Matches the server-side location name column limit (same cap as StorageOverviewViewModel.onNewNameChange). */
private const val MAX_LOCATION_NAME_LENGTH = 50

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorageOverviewScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenLocation: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    // Scoped-down handoff from a scan-originated zero-result search (GAP-5 H6,
    // MainActivity's savedStateHandle delivery): non-null once, right after
    // arriving here from that CTA. There's no attach-on-create wiring yet from
    // here into a shelf's add-product flow — this just tells the user the code
    // is ready and where the code came from is remembered, via a one-shot
    // Snackbar hint; see [onPendingBarcodeConsumed].
    pendingBarcodeCode: String? = null,
    onPendingBarcodeConsumed: () -> Unit = {},
    viewModel: StorageOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var renamingLocation by remember { mutableStateOf<LocationDto?>(null) }
    var renameName by remember { mutableStateOf("") }
    var renameType by remember { mutableStateOf("freezer") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(householdId) { viewModel.load(householdId) }

    val pendingBarcodeHint = stringResource(R.string.storage_pending_barcode_hint)
    LaunchedEffect(pendingBarcodeCode) {
        if (pendingBarcodeCode != null) {
            snackbarHostState.showSnackbar(pendingBarcodeHint)
            onPendingBarcodeConsumed()
        }
    }

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
                        Text(stringResource(R.string.all_storage_title))
                    }
                },
                navigationIcon = {
                    if (state.editMode) {
                        TextButton(onClick = viewModel::exitEditMode) { Text(stringResource(R.string.action_cancel)) }
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
                            onClick = viewModel::requestDelete,
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
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.storage_overview_search_cd),
                            )
                        }
                        if (state.locations.isNotEmpty() && state.canRestructure) {
                            IconButton(onClick = viewModel::enterEditMode) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.storage_overview_edit_cd),
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.editMode) {
                FloatingActionButton(modifier = Modifier.navigationBarsPadding(), onClick = { showAddSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.all_storage_add_location_cd))
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.error?.let {
                    ErrorRetry(message = it, onRetry = viewModel::refresh, modifier = Modifier.padding(16.dp))
                }

                // Don't show the empty text on a failed load — the ErrorRetry above
                // already explains it; "No storages yet" alongside would be a false
                // "your account is empty" (W7).
                if (state.locations.isEmpty() && !state.loading && state.error == null) {
                    Text(
                        text = stringResource(R.string.storage_overview_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }

                if (state.editMode) {
                    // Delete now lives behind edit mode: a swipe should not be able
                    // to destroy a fridge full of food. Keyed by location.id, same
                    // as LocationDetailScreen's shelf list — the server's reorder
                    // response replaces this list wholesale (StorageOverviewViewModel.move),
                    // never merged, so no duplicate key can reach this LazyColumn.
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.locations, key = { _, location -> location.id }) { index, location ->
                            EditableRow(
                                name = location.name,
                                editMode = true,
                                isSystem = false,
                                selected = location.id in state.selected,
                                canMoveUp = index > 0,
                                canMoveDown = index < state.locations.size - 1,
                                // Selecting doesn't touch the network, but rename/reorder each
                                // fire their own request immediately — held off while another
                                // mutation is in flight so two don't race each other.
                                actionsEnabled = !state.loading,
                                // A stable, edit-mode-only handle a driving test can wait on
                                // instead of racing this row's name text against the plain
                                // (non-edit-mode) list/tab rendering it replaces.
                                modifier = Modifier.testTag("location-row-${location.id}"),
                                onClick = { viewModel.toggleSelection(location.id) },
                                onRename = {
                                    renamingLocation = location
                                    renameName = location.name
                                    renameType = location.type
                                },
                                onMoveUp = { viewModel.moveUp(location.id) },
                                onMoveDown = { viewModel.moveDown(location.id) },
                                renameLabelRes = R.string.storage_edit_title,
                                moveUpLabelRes = R.string.storage_move_up_cd,
                                moveDownLabelRes = R.string.storage_move_down_cd,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))

                        state.locations.forEach { location ->
                            // Hoist formatted string for use inside non-composable semantics block
                            val openDesc = stringResource(R.string.storage_overview_open_cd, location.name)
                            FrostCard(
                                onClick = { onOpenLocation(location.id) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .semantics { contentDescription = openDesc },
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = location.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = storageTypeLabel(location.type),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(80.dp))
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
            options = locationStrategyOptions(),
            targets = state.moveTargets,
            onDismiss = viewModel::cancelDelete,
            onConfirm = { strategy, targetId -> viewModel.confirmDelete(strategy, targetId) },
        )
    }

    // Undo snackbar. A snackbar with an action, rather than a one-shot error
    // effect (which has no action slot).
    //
    // GAP5-L1 (known limitation, deliberately not fixed here): if a dialog is opened
    // (rename sheet, add sheet, DeleteStrategyDialog above) while this Undo snackbar
    // is showing, Compose's dialog window sits in its own layer above the Scaffold's
    // SnackbarHost, so the snackbar can be visually obscured or dismissed-looking
    // behind it for that dialog's lifetime — Undo still works if tapped through, but
    // it's not reliably visible. A full fix means either deferring/queuing dialog
    // opens while an undo snackbar is in flight, or moving the snackbar into the same
    // window layer as dialogs (a app-wide z-order rework) — disproportionate for this
    // edge timing case relative to how rarely a user opens another dialog inside the
    // ~few-second Undo window. Left as documented, not fixed.
    val undoLabel = stringResource(R.string.delete_undo)
    val deletedMessage = stringResource(R.string.locations_deleted)
    LaunchedEffect(state.lastBatchId) {
        if (state.lastBatchId == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        } else {
            viewModel.consumeLastBatch()
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
        viewModel.consumeUndoResult()
    }

    renamingLocation?.let { location ->
        ModalBottomSheet(
            onDismissRequest = { renamingLocation = null },
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
                Text(text = stringResource(R.string.storage_edit_title), style = MaterialTheme.typography.titleLarge)

                Text(text = stringResource(R.string.add_storage_type_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STORAGE_TYPES.forEach { type ->
                        FilterChip(
                            selected = renameType == type,
                            onClick = { renameType = type },
                            label = { Text(storageTypeLabel(type)) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it.take(MAX_LOCATION_NAME_LENGTH) },
                        label = { Text(stringResource(R.string.add_storage_name_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.rename(location.id, renameName, renameType)
                            renamingLocation = null
                        },
                        enabled = renameName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_storage_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(text = stringResource(R.string.add_storage_type_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STORAGE_TYPES.forEach { type ->
                        FilterChip(
                            selected = state.newType == type,
                            onClick = { viewModel.onTypeSelect(type) },
                            label = { Text(storageTypeLabel(type)) },
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
                        label = { Text(stringResource(R.string.add_storage_name_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                keyboardController?.hide()
                                viewModel.create()
                                showAddSheet = false
                            }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.create()
                            showAddSheet = false
                        },
                        enabled = !state.loading && state.newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }
        }
    }
}
