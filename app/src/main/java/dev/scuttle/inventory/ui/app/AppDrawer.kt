package dev.scuttle.inventory.ui.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onNavigateHousehold: (Long) -> Unit,
    onNavigateSettings: () -> Unit,
    currentHouseholdId: Long? = null,
) {
    val state by viewModel.state.collectAsState()

    ModalDrawerSheet {
        Spacer(Modifier.height(16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Households") },
            selected = currentHouseholdId == null,
            onClick = onNavigateHome,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (state.households.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
            Text(
                text = "Your households",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )

            state.households.forEach { household ->
                val isDefault = state.defaultHouseholdId == household.id
                NavigationDrawerItem(
                    label = { Text(household.name) },
                    selected = currentHouseholdId == household.id,
                    onClick = { onNavigateHousehold(household.id) },
                    badge = {
                        IconButton(
                            onClick = {
                                if (isDefault) viewModel.clearDefault()
                                else viewModel.setDefault(household.id)
                            },
                        ) {
                            Icon(
                                imageVector = if (isDefault) Icons.Default.Star else Icons.Outlined.Star,
                                contentDescription = if (isDefault) "Remove default" else "Set as default",
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onNavigateSettings,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
