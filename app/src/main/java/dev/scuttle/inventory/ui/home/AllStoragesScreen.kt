package dev.scuttle.inventory.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.HouseholdOption
import dev.scuttle.inventory.ui.common.HouseholdPickerSheet
import dev.scuttle.inventory.ui.common.SnackbarErrorEffect
import dev.scuttle.inventory.ui.common.orderByPosition
import dev.scuttle.inventory.ui.common.storageTypeLabel
import dev.scuttle.inventory.ui.hierarchy.DeleteStrategyDialog
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import dev.scuttle.inventory.ui.hierarchy.locationStrategyOptions
import dev.scuttle.inventory.ui.theme.FrostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllStoragesScreen(
    modifier: Modifier = Modifier,
    viewModel: DrawerViewModel,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenStorage: (householdId: Long) -> Unit = {},
    onOpenSearch: (householdId: Long) -> Unit = {},
    localViewModel: AllStoragesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localState by localViewModel.state.collectAsState()
    val actionErrorRes by viewModel.actionErrorRes.collectAsState()
    // M5: edit mode is checkbox multi-select + a single "delete selected" action
    // opening the DeleteStrategyDialog, matching StorageOverview/LocationDetail's
    // grammar — previously this screen alone used a single per-row delete button.
    // The selection itself now lives in DrawerViewModel (state.editMode/selected),
    // since it's part of the same delete-strategy flow as pendingDelete/moveTargets.
    val editMode = state.editMode

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Surfaces a failed delete (or a failed undo) as a transient snackbar so it
    // isn't silent (W10).
    // H3: actionErrorRes is an R.string.* id, not a raw literal — resolved via stringResource().
    SnackbarErrorEffect(
        error = actionErrorRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeActionError,
    )

    // Blocker 2 (final review): this screen's search icon is a single GLOBAL
    // top-bar action, not tied to any one of the household groups rendered below —
    // there's no "row tapped" to carry a household from. With exactly one household
    // there's nothing to ask; with more than one, hard-coding the FIRST (the bug
    // this fixes) silently made every other household's search reachable only by
    // drilling into that household's own Storage overview first. Ask via the shared
    // picker instead.
    var showHouseholdPicker by rememberSaveable { mutableStateOf(false) }
    val openSearch: () -> Unit = {
        if (state.entries.size > 1) {
            showHouseholdPicker = true
        } else {
            state.entries
                .firstOrNull()
                ?.id
                ?.let(onOpenSearch)
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
                    if (editMode && state.selected.isNotEmpty()) {
                        Text(stringResource(R.string.location_selected_count, state.selected.size))
                    } else {
                        Text(stringResource(R.string.all_storage_title))
                    }
                },
                navigationIcon = {
                    // Aligned to StorageOverview's edit-mode chrome (GAP5-M4): Cancel
                    // lives in the navigationIcon slot, not mixed into actions.
                    if (editMode) {
                        TextButton(onClick = viewModel::exitEditMode) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                },
                actions = {
                    if (editMode) {
                        // Labeled, error-coloured Button showing the count — matches
                        // StorageOverviewScreen's delete affordance exactly (GAP5-M4);
                        // this screen used to show an icon-only Delete button instead.
                        Button(
                            onClick = viewModel::requestDeleteSelected,
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
                        // Search lost its bottom-nav tab (Task 7) but keeps a top-bar
                        // icon here, per spec — opens the household picker with more
                        // than one household (see openSearch above), navigates
                        // straight through with exactly one, no-ops with none.
                        IconButton(onClick = openSearch) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search))
                        }
                        if (state.entries.any { it.locations.isNotEmpty() }) {
                            IconButton(onClick = viewModel::enterEditMode) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.storage_overview_edit_cd),
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                val errorRes = state.errorRes
                if (errorRes != null && state.entries.isEmpty()) {
                    // A load failure must not masquerade as an empty account — show
                    // the error with a retry, not "No storages yet" (W3).
                    ErrorRetry(message = stringResource(errorRes), onRetry = { viewModel.refresh() })
                } else if (state.entries.isEmpty()) {
                    Text(stringResource(R.string.all_storage_empty))
                }

                // Household order is a device-local view preference (D8), never
                // server state — see AllStoragesViewModel.orderedEntries's doc.
                localViewModel.orderedEntries(state.entries).forEach { entry ->
                    key(entry.id) {
                        val isCollapsed = entry.id in localState.collapsedHouseholdIds
                        // KeyboardArrowDown rotated -90° reads as a right-pointing
                        // (collapsed) chevron and animates back to pointing-down
                        // (expanded) — one icon, two states, matching this file's
                        // existing "no new icon asset for a two-state toggle"
                        // idiom (see the favorite star below). Keyed by household
                        // id (like the location loop below) so this per-entry
                        // animation state is never misattributed to a different
                        // household across recompositions.
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (isCollapsed) -90f else 0f,
                            label = "household-chevron-${entry.id}",
                        )
                        val toggleCollapsedCd =
                            if (isCollapsed) {
                                stringResource(R.string.all_storage_expand_household_cd, entry.name)
                            } else {
                                stringResource(R.string.all_storage_collapse_household_cd, entry.name)
                            }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        // Announce the ACTION via onClickLabel rather than
                                        // replacing the row's semantics wholesale — clearAndSetSemantics
                                        // used to sit here and discard every descendant's semantics,
                                        // including the household's own name (Text(entry.name) below),
                                        // so TalkBack lost "Home" entirely and it also silently broke
                                        // every onNodeWithText/hasText("Home") flow test that navigates
                                        // by tapping through the household name (regression, final
                                        // review 2026-07-14). onClickLabel keeps the name readable —
                                        // TalkBack now announces "Home, button, collapse group" — while
                                        // still exposing the collapse/expand action distinctly from a
                                        // plain click.
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = toggleCollapsedCd,
                                        ) {
                                            localViewModel.toggleCollapsed(entry.id)
                                        },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.rotate(chevronRotation),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!editMode) {
                                IconButton(onClick = { onOpenStorage(entry.id) }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.all_storage_add_location_cd),
                                    )
                                }
                            }
                        }

                        // Collapsing only hides these rows -- it never touches
                        // edit mode, favorites, or the delete-confirmation flow
                        // below (state.pendingDelete), which lives outside this
                        // Column entirely and stays reachable regardless of
                        // collapse state. A row that isn't rendered can't be
                        // tapped, so a collapsed group can never carry a pending
                        // delete the user can't see.
                        val orderedLocations =
                            if (isCollapsed) {
                                emptyList()
                            } else {
                                orderByPosition(entry.locations, { it.position }, { it.name })
                            }
                        orderedLocations.forEach { location ->
                            key(location.id) {
                                val hasWarning = state.locationWarnings[location.id] == true
                                val isFavorite = location.id in localState.favoriteLocationIds

                                val rowContent: @Composable () -> Unit = {
                                    Row(
                                        modifier =
                                            Modifier
                                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                                                .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                location.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    storageTypeLabel(location.type),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (hasWarning) {
                                                    Text(
                                                        stringResource(R.string.all_storage_stock_warning),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                }
                                            }
                                        }
                                        if (editMode && entry.canRestructure) {
                                            Checkbox(
                                                checked = location.id in state.selected,
                                                onCheckedChange = {
                                                    viewModel.toggleSelection(entry.id, location.id)
                                                },
                                            )
                                        } else if (!editMode) {
                                            IconButton(onClick = { localViewModel.toggleFavorite(location.id) }) {
                                                Icon(
                                                    if (isFavorite) {
                                                        Icons.Default.Star
                                                    } else {
                                                        Icons.Outlined.StarOutline
                                                    },
                                                    contentDescription =
                                                        if (isFavorite) {
                                                            stringResource(
                                                                R.string.all_storage_favorite_remove_cd,
                                                            )
                                                        } else {
                                                            stringResource(R.string.all_storage_favorite_add_cd)
                                                        },
                                                    tint =
                                                        if (isFavorite) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                )
                                            }
                                        }
                                        // editMode && !entry.canRestructure: neither icon renders — a
                                        // Member's row in edit mode has nothing restructure-capable to
                                        // offer here, and favorite-toggling belongs to the non-edit view.
                                    }
                                }
                                // In edit mode, tapping the row body toggles selection (matching
                                // StorageOverviewScreen's EditableRow), not navigation — same as
                                // the checkbox itself; a Member's row (no canRestructure) has no
                                // selection to toggle, so it keeps navigating even in edit mode.
                                val rowOnClick =
                                    if (editMode && entry.canRestructure) {
                                        { viewModel.toggleSelection(entry.id, location.id) }
                                    } else {
                                        { onOpenLocation(entry.id, location.id) }
                                    }
                                if (hasWarning) {
                                    Card(
                                        onClick = rowOnClick,
                                        modifier =
                                            Modifier.fillMaxWidth().testTag("home-location-${location.name}"),
                                        colors =
                                            CardDefaults.cardColors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.4f,
                                                    ),
                                            ),
                                        content = { rowContent() },
                                    )
                                } else {
                                    FrostCard(
                                        onClick = rowOnClick,
                                        modifier =
                                            Modifier.fillMaxWidth().testTag("home-location-${location.name}"),
                                        content = { rowContent() },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // The delete-strategy dialog for the location just tapped for delete.
    // Non-null pendingDelete is the ONLY thing that shows this dialog, and
    // confirmDelete() (the only path that actually deletes) is reachable
    // exclusively from its confirm button — the per-row trash icon above only
    // ever calls requestDelete().
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

    // H3: tapping a row in a DIFFERENT household than the current selection
    // resets the selection instead of accumulating across households (see
    // DrawerViewModel.toggleSelection's doc comment) — that used to be silent,
    // the count just changed. Surface it as a short snackbar naming the new
    // household.
    LaunchedEffect(state.selectionResetEvent) {
        val householdName = state.selectionResetEvent ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.getString(R.string.selection_cleared_switched_household, householdName),
        )
        viewModel.consumeSelectionResetEvent()
    }

    if (showHouseholdPicker) {
        HouseholdPickerSheet(
            households = state.entries.map { HouseholdOption(it.id, it.name) },
            onDismiss = { showHouseholdPicker = false },
            onPick = { householdId ->
                showHouseholdPicker = false
                onOpenSearch(householdId)
            },
        )
    }
}
