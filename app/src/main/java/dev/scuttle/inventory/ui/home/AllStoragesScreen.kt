package dev.scuttle.inventory.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.HouseholdOption
import dev.scuttle.inventory.ui.common.HouseholdPickerSheet
import dev.scuttle.inventory.ui.common.SnackbarErrorEffect
import dev.scuttle.inventory.ui.common.orderByPosition
import dev.scuttle.inventory.ui.common.storageTypeLabel
import dev.scuttle.inventory.ui.storage.STORAGE_TYPES
import dev.scuttle.inventory.ui.theme.FrostCard
import dev.scuttle.inventory.ui.theme.WARNING_TINT_ALPHA

/** Matches the server-side location name column limit (same cap StorageOverviewScreen uses). */
private const val MAX_LOCATION_NAME_LENGTH = 50

/**
 * Home/AllStorages: a clean, BROWSE-ONLY list of every household's storage
 * locations. Editing (add/rename/delete/reorder) lives entirely on the
 * per-household Storage overview screen (Routes.storage(hhId)) — this screen's
 * gear icon and FAB both route there (directly with one household, via
 * [HouseholdPickerSheet] with more than one) instead of offering any edit
 * affordance inline. See CLAUDE.md's Navigation section for the full picture.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllStoragesScreen(
    modifier: Modifier = Modifier,
    viewModel: DrawerViewModel,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenStorage: (householdId: Long) -> Unit = {},
    localViewModel: AllStoragesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localState by localViewModel.state.collectAsState()
    val actionErrorRes by viewModel.actionErrorRes.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    // Surfaces a failed create-location as a transient snackbar (W10).
    SnackbarErrorEffect(
        error = actionErrorRes?.let { stringResource(it) },
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeActionError,
    )

    // One shared household picker, reused by both the gear (edit) and the FAB
    // (add) — [pickerPurpose] tracks which gesture opened it so onPick can
    // route to the right place instead of the two flows fighting over one
    // boolean.
    var pickerPurpose by rememberSaveable { mutableStateOf<HouseholdPickerPurpose?>(null) }
    var showAddSheetForHousehold by rememberSaveable { mutableStateOf<Long?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newType by rememberSaveable { mutableStateOf("freezer") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val openEdit: () -> Unit = {
        val households = state.entries
        if (households.size > 1) {
            pickerPurpose = HouseholdPickerPurpose.EDIT
        } else {
            households.firstOrNull()?.id?.let(onOpenStorage)
        }
    }
    val openAdd: () -> Unit = {
        val households = state.entries
        if (households.size > 1) {
            pickerPurpose = HouseholdPickerPurpose.ADD
        } else {
            households.firstOrNull()?.id?.let { showAddSheetForHousehold = it }
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
                    // Unlike the old inline edit-mode pencil (only worth showing
                    // when there was something to select/reorder), the gear just
                    // NAVIGATES to a household's Storage overview — which has its
                    // own add-storage FAB and empty state — so it's available
                    // whenever there is at least one household, even an empty one.
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = openEdit) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.all_storage_manage_cd),
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.entries.isNotEmpty()) {
                FloatingActionButton(modifier = Modifier.navigationBarsPadding(), onClick = openAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.all_storage_add_location_cd))
                }
            }
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
                        // idiom. Keyed by household id so this per-entry animation
                        // state is never misattributed to a different household
                        // across recompositions.
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, start = 4.dp)
                                    // Announce the ACTION via onClickLabel rather than
                                    // replacing the row's semantics wholesale — see the
                                    // history in this file's earlier revisions for why
                                    // clearAndSetSemantics is the wrong tool here.
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = toggleCollapsedCd,
                                    ) {
                                        localViewModel.toggleCollapsed(entry.id)
                                    },
                            horizontalArrangement = Arrangement.Start,
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

                        // Collapsing only hides these rows -- it never touches
                        // any dialog/sheet state, which lives outside this Column
                        // entirely.
                        val orderedLocations =
                            if (isCollapsed) {
                                emptyList()
                            } else {
                                orderByPosition(entry.locations, { it.position }, { it.name })
                            }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            orderedLocations.forEach { location ->
                                key(location.id) {
                                    LocationRow(
                                        entry = entry,
                                        location = location,
                                        hasWarning = state.locationWarnings[location.id] == true,
                                        onOpenLocation = onOpenLocation,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(96.dp))
            }
        }
    }

    if (pickerPurpose != null) {
        val purpose = pickerPurpose
        HouseholdPickerSheet(
            households = state.entries.map { HouseholdOption(it.id, it.name) },
            title =
                when (purpose) {
                    HouseholdPickerPurpose.ADD -> stringResource(R.string.all_storage_add_choose_household_title)
                    else -> stringResource(R.string.all_storage_edit_choose_household_title)
                },
            onDismiss = { pickerPurpose = null },
            onPick = { householdId ->
                pickerPurpose = null
                when (purpose) {
                    HouseholdPickerPurpose.ADD -> showAddSheetForHousehold = householdId
                    else -> onOpenStorage(householdId)
                }
            },
        )
    }

    val addHouseholdId = showAddSheetForHousehold
    if (addHouseholdId != null) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheetForHousehold = null },
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
                            selected = newType == type,
                            onClick = { newType = type },
                            label = { Text(storageTypeLabel(type)) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(MAX_LOCATION_NAME_LENGTH) },
                        label = { Text(stringResource(R.string.add_storage_name_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                keyboardController?.hide()
                                viewModel.createLocation(addHouseholdId, newName, newType)
                                newName = ""
                                showAddSheetForHousehold = null
                            }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.createLocation(addHouseholdId, newName, newType)
                            newName = ""
                            showAddSheetForHousehold = null
                        },
                        enabled = newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }
        }
    }
}

/** Which gesture opened the shared [HouseholdPickerSheet] — see its call site's doc comment. */
private enum class HouseholdPickerPurpose { EDIT, ADD }

/**
 * One location row — browse-only now: tapping it always navigates to that
 * location. Pulled out of the per-household loop for the same readability
 * reason it was pulled out before edit mode existed at all.
 */
@Composable
private fun LocationRow(
    entry: HouseholdWithLocations,
    location: LocationDto,
    hasWarning: Boolean,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    location.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
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
        }
    }
    val rowOnClick = { onOpenLocation(entry.id, location.id) }
    // Warning rows keep FrostCard's geometry (corner radius + border) and differ by
    // tint only — a plain Material Card here rendered visibly smaller than its
    // siblings (smaller corner radius, no hairline border).
    FrostCard(
        onClick = rowOnClick,
        containerColor =
            if (hasWarning) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = WARNING_TINT_ALPHA)
            } else {
                null
            },
        modifier = Modifier.fillMaxWidth().testTag("home-location-${location.name}"),
        content = { rowContent() },
    )
}
