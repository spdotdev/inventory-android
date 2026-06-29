package dev.scuttle.inventory.ui.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawer(
    viewModel: DrawerViewModel,
    onNavigateHome: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateLocation: (householdId: Long, locationId: Long) -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    ModalDrawerSheet {
        Spacer(Modifier.height(16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("All storage") },
            selected = false,
            onClick = onNavigateHome,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text("Dashboard") },
            selected = false,
            onClick = onNavigateDashboard,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (state.entries.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

            // Scrollable location list in the middle
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
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
                                    color = if (hasWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            selected = false,
                            onClick = { onNavigateLocation(entry.id, location.id) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onNavigateSettings,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(Modifier.height(8.dp))
    }
}
