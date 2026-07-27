package dev.scuttle.inventory.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.shelfDisplayName
import dev.scuttle.inventory.ui.hierarchy.DeleteStrategyDialog
import dev.scuttle.inventory.ui.hierarchy.EditableRow
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.hierarchy.shelfStrategyOptions
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import dev.scuttle.inventory.ui.theme.ShelfAvatar

/** Size of the themed avatar shown before each shelf row's name. */
private val SHELF_AVATAR_SIZE = 28.dp

/**
 * The dedicated shelf-management page reached from LocationDetailScreen's top-bar
 * gear icon (same relocation the storage-tab rework did for locations: gear →
 * dedicated screen, see CLAUDE.md's "Editing the hierarchy" paragraph).
 *
 * Unlike StorageOverviewScreen (which still toggles an inline edit-mode pencil),
 * this screen IS the edit mode — always on, no toggle — since shelf management has
 * no other reason to exist on this page. It reuses [ShelvesViewModel] verbatim
 * (rename/reorder/delete/add/undo all already live there) via its own Hilt-scoped
 * instance, driven into edit mode once on arrival.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageShelvesScreen(
    householdId: Long,
    locationId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    // Rename moved off this screen onto the full-page ShelfEditScreen (mirrors
    // the household edit page) — the pencil now navigates instead of opening an
    // inline dialog. Never invoked for the is_system shelf: EditableRow's own
    // `isSystem` gate hides the pencil entirely for that row.
    onEditShelf: (shelfId: Long) -> Unit = {},
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
    val state by shelvesViewModel.state.collectAsState()
    var showAddShelfSheet by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val nonSystemShelfCount = state.shelves.count { !it.is_system }

    LaunchedEffect(householdId, locationId) {
        shelvesViewModel.load(householdId, locationId)
        shelvesViewModel.enterEditMode()
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
                    if (state.selected.isNotEmpty()) {
                        Text(stringResource(R.string.location_selected_count, state.selected.size))
                    } else {
                        Text(stringResource(R.string.shelves_manage_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    Button(
                        // requestDelete() only OPENS the strategy dialog — the actual
                        // delete happens in confirmDelete(), wired to the dialog below.
                        // This button must never call confirmDelete() directly: that
                        // would be exactly the no-confirmation bug the Deletes section
                        // of CLAUDE.md exists to prevent.
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
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.navigationBarsPadding(),
                onClick = { showAddShelfSheet = true },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.location_add_shelf_cd))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = shelvesViewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.errorRes?.let {
                    ErrorRetry(
                        message = stringResource(it),
                        onRetry = shelvesViewModel::refresh,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                if (state.shelves.isEmpty() && !state.loading && state.errorRes == null) {
                    Text(
                        text = stringResource(R.string.location_no_shelves),
                        modifier = Modifier.padding(16.dp),
                    )
                }

                // Same LOCKED gating EditableRow/ShelvesViewModel already apply: the
                // Unsorted (is_system) shelf is never renamable, never selectable,
                // never a reorder target, and always sorts last — orderShelves()
                // already guarantees the last part, this list just renders in order.
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .navigationBarsPadding(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.shelves, key = { _, shelf -> shelf.id }) { index, shelf ->
                        EditableRow(
                            name = shelfDisplayName(shelf),
                            editMode = true,
                            isSystem = shelf.is_system,
                            selected = shelf.id in state.selected,
                            canMoveUp = !shelf.is_system && index > 0,
                            canMoveDown = !shelf.is_system && index < nonSystemShelfCount - 1,
                            actionsEnabled = !state.loading,
                            // Same shape as LocationDetailScreen's own shelf-row-<id> tag
                            // (this screen is that tag's new home).
                            modifier = Modifier.testTag("shelf-row-${shelf.id}"),
                            leadingIcon = {
                                ShelfAvatar(
                                    shelfId = shelf.id,
                                    size = SHELF_AVATAR_SIZE,
                                    colorKey = shelf.color,
                                    iconKey = shelf.icon,
                                )
                            },
                            onClick = { shelvesViewModel.toggleSelection(shelf.id) },
                            onRename = { onEditShelf(shelf.id) },
                            onMoveUp = { shelvesViewModel.moveUp(shelf.id) },
                            onMoveDown = { shelvesViewModel.moveDown(shelf.id) },
                        )
                    }
                }
            }
        }
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

    // Undo snackbar. A snackbar with an action, rather than a one-shot error effect
    // (which has no action slot).
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
    // undo window) shows the specific message instead of a generic error.
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
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
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
            }
        }
    }
}
