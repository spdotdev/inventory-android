package dev.scuttle.inventory.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R

@Composable
fun AppDrawer(
    viewModel: DrawerViewModel,
    onNavigateHome: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateSearch: (householdId: Long) -> Unit,
    onNavigateHouseholds: () -> Unit,
    onNavigateMissingItems: () -> Unit,
    onNavigateLocation: (householdId: Long, locationId: Long) -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // Use a raw Surface so we can control the layout ourselves.
    // ModalDrawerSheet adds an internal verticalScroll which breaks weight-based pinning.
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(DrawerDefaults.MaximumDrawerWidth),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DrawerDefaults.ModalDrawerElevation,
        shape = DrawerDefaults.shape,
    ) {
        Column(modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text(stringResource(R.string.drawer_all_storage)) },
                selected = false,
                onClick = onNavigateHome,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                label = { Text(stringResource(R.string.drawer_dashboard)) },
                selected = false,
                onClick = onNavigateDashboard,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            val firstHouseholdId = state.entries.firstOrNull()?.id
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.drawer_search)) },
                selected = false,
                onClick = { firstHouseholdId?.let(onNavigateSearch) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                label = { Text(stringResource(R.string.drawer_households)) },
                selected = false,
                onClick = onNavigateHouseholds,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            val missingCount = state.missingItemCount
            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (missingCount > 0) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = {
                    Text(
                        text = if (missingCount > 0) stringResource(R.string.drawer_missing_items_count, missingCount)
                               else stringResource(R.string.drawer_missing_items),
                        color = if (missingCount > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                selected = false,
                onClick = onNavigateMissingItems,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

            // Scrollable middle section — takes all remaining space
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                state.entries.forEach { entry ->
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                    )
                    entry.locations.forEach { location ->
                        val hasWarning = state.locationWarnings[location.id] == true
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = if (hasWarning) "⚠ ${location.name}" else location.name,
                                    color = if (hasWarning) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            selected = false,
                            onClick = { onNavigateLocation(entry.id, location.id) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }

            // Settings pinned to bottom
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.drawer_settings)) },
                selected = false,
                onClick = onNavigateSettings,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

        }
    }
}
