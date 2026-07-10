package dev.scuttle.inventory.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.LowStockItem
import dev.scuttle.inventory.ui.common.LiveStatusText
import dev.scuttle.inventory.ui.theme.FrostCard

/**
 * Distinct from the plain text "Dashboard", which also appears as the
 * always-composed drawer nav item label (ModalNavigationDrawer keeps drawer
 * content in the tree even when closed) — tests must target this tag, not
 * the text, to avoid matching the closed drawer instead of this screen.
 */
const val DASHBOARD_TITLE_TEST_TAG = "dashboard_top_bar_title"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenHouseholds: () -> Unit = {},
    onOpenMissingItems: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    if (state.hasNoHouseholds && !state.loading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.dashboard_welcome_title)) },
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
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
                actions = {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { LiveStatusText(it) }

            // Stat cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(label = stringResource(R.string.dashboard_stat_locations), value = state.totalLocations.toString(), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.dashboard_stat_shelves), value = state.totalShelves.toString(), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.dashboard_stat_products), value = state.totalProducts.toString(), modifier = Modifier.weight(1f))
            }

            if (state.mandatoryWarnings > 0) {
                MissingItemsCard(count = state.mandatoryWarnings, onClick = onOpenMissingItems)
            }

            // Phase 2: products at/below their low-stock threshold (missing items
            // excluded — they're already in the red card above).
            if (state.lowStockItems.isNotEmpty()) {
                Text(stringResource(R.string.dashboard_running_low), style = MaterialTheme.typography.titleMedium)
                RunningLowCard(state.lowStockItems)
            }

            // Bar chart
            if (state.locationStats.isNotEmpty()) {
                Text(stringResource(R.string.dashboard_products_by_location), style = MaterialTheme.typography.titleMedium)
                FrostCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val maxVal = state.locationStats.maxOfOrNull { it.productCount } ?: 1
                        state.locationStats.forEach { stat ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stat.location.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(12.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall),
                                    ) {
                                        val fraction = if (maxVal > 0) stat.productCount.toFloat() / maxVal else 0f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                                .height(12.dp)
                                                .background(primaryColor, MaterialTheme.shapes.extraSmall),
                                        )
                                    }
                                    Text(
                                        stat.productCount.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Favorite locations
            if (state.favoriteLocationIds.isNotEmpty()) {
                Text(stringResource(R.string.dashboard_favorite_locations), style = MaterialTheme.typography.titleMedium)
                state.locationStats
                    .filter { it.location.id in state.favoriteLocationIds }
                    .forEach { stat ->
                        FrostCard(
                            onClick = { onOpenLocation(stat.householdId, stat.location.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stat.location.name, style = MaterialTheme.typography.bodyLarge)
                                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
            }

            // Favorite shelves
            if (state.favoriteShelves.isNotEmpty()) {
                Text(stringResource(R.string.dashboard_favorite_shelves), style = MaterialTheme.typography.titleMedium)
                state.favoriteShelves.forEach { entry ->
                    FrostCard(
                        onClick = { onOpenLocation(entry.householdId, entry.shelf.location_id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.shelf.name, style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
        } // end PullToRefreshBox
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    FrostCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RunningLowCard(items: List<LowStockItem>) {
    FrostCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.bodyLarge)
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
private fun MissingItemsCard(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column {
                Text(
                    if (count == 1) stringResource(R.string.dashboard_missing_one)
                    else stringResource(R.string.dashboard_missing_many, count),
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
