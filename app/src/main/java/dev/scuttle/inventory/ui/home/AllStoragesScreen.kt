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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
    val actionError by viewModel.actionError.collectAsState()
    // Delete now lives behind edit mode: a swipe should not be able to destroy a
    // fridge full of food. Mirrors StorageOverviewScreen's own edit-mode gate
    // (Task 5) — kept as plain Compose state here rather than in DrawerViewModel
    // because it's purely a "which icon does this row show" toggle, not a
    // network-backed concern the way pendingDelete/moveTargets below are.
    var editMode by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    // Surfaces a failed delete (or a failed undo) as a transient snackbar so it
    // isn't silent (W10).
    SnackbarErrorEffect(actionError, snackbarHostState, onConsumed = viewModel::consumeActionError)

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
                title = { Text(stringResource(R.string.all_storage_title)) },
                actions = {
                    if (editMode) {
                        TextButton(onClick = { editMode = false }) {
                            Text(stringResource(R.string.action_cancel))
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
                            IconButton(onClick = { editMode = true }) {
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

                val error = state.error
                if (error != null && state.entries.isEmpty()) {
                    // A load failure must not masquerade as an empty account — show
                    // the error with a retry, not "No storages yet" (W3).
                    ErrorRetry(message = error, onRetry = { viewModel.refresh() })
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
                                        .clickable(role = Role.Button) {
                                            localViewModel.toggleCollapsed(entry.id)
                                        }.clearAndSetSemantics { contentDescription = toggleCollapsedCd },
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
                                        if (editMode) {
                                            IconButton(
                                                onClick = { viewModel.requestDelete(entry.id, location.id) },
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.action_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        } else {
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
                                    }
                                }
                                if (hasWarning) {
                                    Card(
                                        onClick = { onOpenLocation(entry.id, location.id) },
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
                                        onClick = { onOpenLocation(entry.id, location.id) },
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
