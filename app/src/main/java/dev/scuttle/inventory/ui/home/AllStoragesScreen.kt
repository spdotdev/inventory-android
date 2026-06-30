package dev.scuttle.inventory.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.app.HouseholdWithLocations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllStoragesScreen(
    modifier: Modifier = Modifier,
    viewModel: DrawerViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenStorage: (householdId: Long) -> Unit = {},
    localViewModel: AllStoragesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localState by localViewModel.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<Pair<HouseholdWithLocations, LocationDto>?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.all_storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                if (state.entries.isEmpty()) {
                    Text(stringResource(R.string.all_storage_empty))
                }

                state.entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { onOpenStorage(entry.id) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.all_storage_add_location_cd))
                        }
                    }

                    entry.locations.forEach { location ->
                        key(location.id) {
                            val hasWarning = state.locationWarnings[location.id] == true
                            val isFavorite = location.id in localState.favoriteLocationIds

                            val swipeState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        pendingDelete = entry to location
                                    }
                                    false
                                },
                            )
                            SwipeToDismissBox(
                                state = swipeState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = MaterialTheme.shapes.medium,
                                            ),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                        )
                                    }
                                },
                            ) {
                                Card(
                                    onClick = { onOpenLocation(entry.id, location.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = if (hasWarning)
                                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                    else
                                        CardDefaults.cardColors(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(location.name, style = MaterialTheme.typography.bodyLarge)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    location.type,
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
                                        IconButton(onClick = { localViewModel.toggleFavorite(location.id) }) {
                                            Icon(
                                                if (isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                                                contentDescription = if (isFavorite) stringResource(R.string.all_storage_favorite_remove_cd) else stringResource(R.string.all_storage_favorite_add_cd),
                                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pendingDelete?.let { (entry, location) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_dialog_location_title, location.name)) },
            text = { Text(stringResource(R.string.delete_dialog_location_text)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteLocation(entry.id, location.id); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
