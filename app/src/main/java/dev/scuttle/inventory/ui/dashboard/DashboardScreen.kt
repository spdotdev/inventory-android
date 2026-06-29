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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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

    LaunchedEffect(Unit) { viewModel.refresh() }

    if (state.hasNoHouseholds && !state.loading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Welcome!") },
            text = { Text("You're not part of any household yet. Create a new one or join an existing one with an invite code.") },
            confirmButton = {
                Button(onClick = onOpenHouseholds) { Text("Create household") }
            },
            dismissButton = {
                OutlinedButton(onClick = onOpenHouseholds) { Text("Join with invite") }
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
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
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
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // Stat cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(label = "Locations", value = state.totalLocations.toString(), modifier = Modifier.weight(1f))
                StatCard(label = "Shelves", value = state.totalShelves.toString(), modifier = Modifier.weight(1f))
                StatCard(label = "Products", value = state.totalProducts.toString(), modifier = Modifier.weight(1f))
            }

            if (state.mandatoryWarnings > 0) {
                Card(
                    onClick = onOpenMissingItems,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "${state.mandatoryWarnings} mandatory product${if (state.mandatoryWarnings > 1) "s" else ""} at 0",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Bar chart
            if (state.locationStats.isNotEmpty()) {
                Text("Products by location", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
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
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                                    ) {
                                        val fraction = if (maxVal > 0) stat.productCount.toFloat() / maxVal else 0f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                                .height(12.dp)
                                                .background(primaryColor, RoundedCornerShape(4.dp)),
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
                Text("Favorite locations", style = MaterialTheme.typography.titleMedium)
                state.locationStats
                    .filter { it.location.id in state.favoriteLocationIds }
                    .forEach { stat ->
                        Card(
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

            Spacer(Modifier.height(24.dp))
        }
        } // end PullToRefreshBox
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
