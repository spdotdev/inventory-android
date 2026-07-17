package dev.scuttle.inventory.ui.missing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.HouseholdOption
import dev.scuttle.inventory.ui.common.HouseholdPickerSheet
import dev.scuttle.inventory.ui.theme.FrostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingItemsScreen(
    onBack: () -> Unit,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit,
    // MissingItemsUiState has no household list of its own (only items, which are
    // empty in the common case this button matters least) — the caller passes the
    // same household list HierarchyStore already resolves for Dashboard/Home, so
    // this screen's own search icon can ask (Blocker 2, final review) rather than
    // silently hard-coding the first one when there's more than one household.
    households: List<HouseholdOption> = emptyList(),
    onOpenSearch: (householdId: Long) -> Unit = {},
    viewModel: MissingItemsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showHouseholdPicker by rememberSaveable { mutableStateOf(false) }
    val openSearch: () -> Unit = {
        if (households.size > 1) {
            showHouseholdPicker = true
        } else {
            households.firstOrNull()?.id?.let(onOpenSearch)
        }
    }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.missing_items_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Search lost its bottom-nav tab (Task 7) but keeps a top-bar icon
                    // here, per spec — opens the household picker with more than one
                    // household (see openSearch above), navigates straight through
                    // with exactly one, no-ops with none.
                    IconButton(onClick = openSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search))
                    }
                    if (state.items.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.missing_items_warning_cd),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
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
            // First-load only — matches StorageOverviewScreen/LocationDetailScreen's
            // idiom. Guarded by state.items.isEmpty() too so this never reappears on a
            // background pull-to-refresh once content has loaded once.
            if (state.loading && state.items.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val error = state.error
            if (error != null && state.items.isEmpty()) {
                // A failed load must not fall through to "all stocked" — for a
                // screen whose whole job is surfacing warnings that's the worst
                // failure mode. Show the error with a retry instead (W4).
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ErrorRetry(message = error, onRetry = viewModel::refresh)
                }
            } else if (state.items.isEmpty() && !state.loading) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.missing_items_empty))
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { /* top spacing */ }
                    items(state.items) { item ->
                        FrostCard(
                            onClick = { onOpenLocation(item.householdId, item.locationId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = item.productName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "${item.locationName} · ${item.shelfName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item { /* bottom spacing */ }
                }
            }
        }
    }

    if (showHouseholdPicker) {
        HouseholdPickerSheet(
            households = households,
            onDismiss = { showHouseholdPicker = false },
            onPick = { householdId ->
                showHouseholdPicker = false
                onOpenSearch(householdId)
            },
        )
    }
}
