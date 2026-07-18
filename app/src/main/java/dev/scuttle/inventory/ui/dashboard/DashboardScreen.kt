package dev.scuttle.inventory.ui.dashboard

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.LowStockItem
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.HouseholdOption
import dev.scuttle.inventory.ui.common.HouseholdPickerSheet
import dev.scuttle.inventory.ui.common.shelfDisplayName
import dev.scuttle.inventory.ui.theme.FrostCard
import dev.scuttle.inventory.ui.theme.HouseholdAvatar
import dev.scuttle.inventory.ui.theme.Spacing

/**
 * Distinct from the plain text "Dashboard", which also appears as the bottom-nav
 * tab label — tests must target this tag, not the text, to avoid matching the
 * nav bar item instead of this screen's title.
 */
const val DASHBOARD_TITLE_TEST_TAG = "dashboard_top_bar_title"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenHouseholds: () -> Unit = {},
    onOpenMissingItems: () -> Unit = {},
    onOpenAllStorage: () -> Unit = {},
    onOpenSearch: (householdId: Long) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // The household a row belongs to, or null when there's only one household and
    // saying so would just be noise (#33).
    val badgeFor: (Long) -> DashboardHousehold? = { householdId ->
        if (state.showHouseholdAttribution) state.householdFor(householdId) else null
    }

    // Blocker 2 (final review): Dashboard's search entry points (the top-bar icon
    // and the products stat card below) are both GLOBAL actions — neither is tied
    // to a specific row/household the way a favorite or a running-low item is — so
    // there's no context to carry the way there is for a per-row tap. With exactly
    // one household there's nothing to ask; with more than one, hard-coding the
    // FIRST (the bug this fixes) silently made every other household's search
    // unreachable from here. Ask instead via the shared picker.
    var showHouseholdPicker by rememberSaveable { mutableStateOf(false) }
    val openSearch: () -> Unit = {
        if (state.households.size > 1) {
            showHouseholdPicker = true
        } else {
            state.firstHouseholdId?.let(onOpenSearch)
        }
    }

    if (state.hasNoHouseholds && !state.loading) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    stringResource(R.string.dashboard_welcome_title),
                    modifier = Modifier.semantics { heading() },
                )
            },
            text = { Text(stringResource(R.string.dashboard_welcome_text)) },
            confirmButton = {
                Button(onClick = onOpenHouseholds) { Text(stringResource(R.string.dashboard_create_household)) }
            },
            dismissButton = {
                OutlinedButton(onClick = onOpenHouseholds) { Text(stringResource(R.string.dashboard_join_with_invite)) }
            },
        )
    }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(
                        stringResource(R.string.dashboard_title),
                        modifier = Modifier.testTag(DASHBOARD_TITLE_TEST_TAG),
                    )
                },
                actions = {
                    // Search lost its bottom-nav tab (Task 7) but keeps this top-bar
                    // icon, per spec — an occasional "where did I put it", not a daily
                    // destination. Guarded the same way the products stat card below
                    // is: nothing to search without at least one household.
                    IconButton(onClick = openSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search))
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
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
                    .padding(padding)
                    .navigationBarsPadding(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // First-load only — matches StorageOverviewScreen/LocationDetailScreen's
                // idiom. state.households stays empty only until the initial fetch
                // resolves, so this never reappears on a pull-to-refresh once content
                // has loaded once.
                if (state.loading && state.households.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.errorRes?.let { ErrorRetry(stringResource(it), onRetry = viewModel::refresh) }

                // M11: distinct from the "no household at all" dialog above — this is
                // an active household with zero locations. Gated on locationStats
                // itself (not a stat number, which can read 0 for other reasons too,
                // e.g. a household with locations but no products yet) so it only
                // appears when there's genuinely nothing to show, and replaces the
                // otherwise-bare all-zero stat row rather than sitting alongside it.
                if (state.households.isNotEmpty() && state.locationStats.isEmpty() && !state.loading) {
                    EmptyLocationsCard(onAddLocation = onOpenAllStorage)
                } else {
                    // Stat cards. The caption belongs to the row, so they're grouped in
                    // their own Column rather than taking the page's 16dp rhythm.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCard(
                                label = stringResource(R.string.dashboard_stat_locations),
                                value = state.totalLocations.toString(),
                                modifier = Modifier.weight(1f),
                                onClick = onOpenAllStorage,
                            )
                            StatCard(
                                label = stringResource(R.string.dashboard_stat_shelves),
                                value = state.totalShelves.toString(),
                                modifier = Modifier.weight(1f),
                                onClick = onOpenAllStorage,
                            )
                            StatCard(
                                label = stringResource(R.string.dashboard_stat_products),
                                value = state.totalProducts.toString(),
                                modifier = Modifier.weight(1f),
                                onClick = openSearch,
                            )
                        }

                        // These numbers sum every household; say so rather than let them
                        // read as one household's (#33).
                        if (state.showHouseholdAttribution) {
                            Text(
                                stringResource(R.string.dashboard_across_households, state.households.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }

                if (state.mandatoryWarnings > 0) {
                    MissingItemsCard(count = state.mandatoryWarnings, onClick = onOpenMissingItems)
                }

                // Phase 2: products at/below their low-stock threshold (missing items
                // excluded — they're already in the red card above).
                if (state.lowStockItems.isNotEmpty()) {
                    Text(stringResource(R.string.dashboard_running_low), style = MaterialTheme.typography.titleMedium)
                    RunningLowCard(
                        items = state.lowStockItems,
                        badgeFor = badgeFor,
                        onOpenLocation = onOpenLocation,
                    )
                }

                // GAP6-M3: locations exist but nothing's been added to them yet — the bar
                // chart below would otherwise render as an all-zero row set with no
                // explanation of why. A light-touch nudge, not a replacement for the stat
                // cards above (which still show the real 0 counts).
                if (state.totalProducts == 0 && state.locationStats.isNotEmpty()) {
                    NoProductsYetCard(onOpenAllStorage = onOpenAllStorage)
                }

                // Bar chart, grouped per household when there's more than one (#33)
                if (state.locationStats.isNotEmpty()) {
                    Text(
                        stringResource(R.string.dashboard_products_by_location),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    DashboardLocationChart(
                        groups = state.groupedLocationStats,
                        maxProductCount = state.maxLocationProductCount,
                        showHouseholdHeaders = state.showHouseholdAttribution,
                        onOpenLocation = onOpenLocation,
                    )
                }

                // Favorite locations
                if (state.favoriteLocationIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.dashboard_favorite_locations),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.locationStats
                        .filter { it.location.id in state.favoriteLocationIds }
                        .forEach { stat ->
                            FavoriteRow(
                                name = stat.location.name,
                                household = badgeFor(stat.householdId),
                                onClick = { onOpenLocation(stat.householdId, stat.location.id) },
                            )
                        }
                }

                // Favorite shelves
                if (state.favoriteShelves.isNotEmpty()) {
                    Text(
                        stringResource(R.string.dashboard_favorite_shelves),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.favoriteShelves.forEach { entry ->
                        FavoriteRow(
                            name = shelfDisplayName(entry.shelf),
                            household = badgeFor(entry.householdId),
                            onClick = { onOpenLocation(entry.householdId, entry.shelf.location_id) },
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
            }
        } // end PullToRefreshBox
    }

    if (showHouseholdPicker) {
        HouseholdPickerSheet(
            households = state.households.map { HouseholdOption(it.id, it.name) },
            onDismiss = { showHouseholdPicker = false },
            onPick = { householdId ->
                showHouseholdPicker = false
                onOpenSearch(householdId)
            },
        )
    }
}

/** M11: zero-state shown when the active household has no locations yet. */
@Composable
private fun EmptyLocationsCard(onAddLocation: () -> Unit) {
    FrostCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.dashboard_empty_locations_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.dashboard_empty_locations_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddLocation) {
                Text(stringResource(R.string.dashboard_empty_locations_cta))
            }
        }
    }
}

/**
 * GAP6-M3: a light-touch nudge for "locations exist, zero products yet" — distinct from
 * [EmptyLocationsCard] (which covers zero LOCATIONS). A plain text row, not a full
 * FrostCard treatment, so it reads as a hint rather than another empty-state block.
 */
@Composable
private fun NoProductsYetCard(onOpenAllStorage: () -> Unit) {
    FrostCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAllStorage) {
        Text(
            stringResource(R.string.dashboard_no_products_yet_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    FrostCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The household badge for a row, sized for a list item. Carries the household name
 * as its content description — on these flat lists the avatar is the only thing
 * saying which household the row belongs to, so it must not be silent to TalkBack.
 */
@Composable
private fun HouseholdBadge(household: DashboardHousehold) {
    HouseholdAvatar(
        householdId = household.id,
        colorKey = household.color,
        iconKey = household.icon,
        size = Spacing.lg,
        contentDescription = household.name,
    )
}

/** A favorited location or shelf. [household] is null when there's nothing to disambiguate. */
@Composable
private fun FavoriteRow(
    name: String,
    household: DashboardHousehold?,
    onClick: () -> Unit,
) {
    FrostCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .padding(Spacing.md)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            household?.let { HouseholdBadge(it) }
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RunningLowCard(
    items: List<LowStockItem>,
    badgeFor: (Long) -> DashboardHousehold?,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit,
) {
    FrostCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLocation(item.householdId, item.locationId) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    badgeFor(item.householdId)?.let { HouseholdBadge(it) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.productName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${item.locationName} › ${item.shelfName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.dashboard_low_stock_qty, item.quantity, item.threshold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingItemsCard(
    count: Int,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column {
                Text(
                    if (count == 1) {
                        stringResource(R.string.dashboard_missing_one)
                    } else {
                        stringResource(R.string.dashboard_missing_many, count)
                    },
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.dashboard_tap_to_restock),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
