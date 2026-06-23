package dev.scuttle.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.scuttle.inventory.ui.auth.AuthScreen
import dev.scuttle.inventory.ui.auth.AuthViewModel
import dev.scuttle.inventory.ui.households.HouseholdsScreen
import dev.scuttle.inventory.ui.invite.InviteScreen
import dev.scuttle.inventory.ui.products.ProductsScreen
import dev.scuttle.inventory.ui.search.SearchScreen
import dev.scuttle.inventory.ui.shelves.ShelvesScreen
import dev.scuttle.inventory.ui.storage.StorageOverviewScreen
import dev.scuttle.inventory.ui.theme.InventoryTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InventoryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

// Minimal state-based navigation until Navigation-Compose lands (see ROADMAP).
@Composable
private fun Root(authViewModel: AuthViewModel = hiltViewModel()) {
    val authState by authViewModel.state.collectAsState()
    var openHouseholdId: Long? by rememberSaveable { mutableStateOf(null) }
    var openLocationId: Long? by rememberSaveable { mutableStateOf(null) }
    var openShelfId: Long? by rememberSaveable { mutableStateOf(null) }
    var showSearch: Boolean by rememberSaveable { mutableStateOf(false) }
    var showInvite: Boolean by rememberSaveable { mutableStateOf(false) }

    when {
        !authState.authenticated -> AuthScreen(viewModel = authViewModel)
        openHouseholdId == null -> HouseholdsScreen(onOpenHousehold = { openHouseholdId = it })
        showSearch -> SearchScreen(
            householdId = openHouseholdId!!,
            onBack = { showSearch = false },
        )
        showInvite -> InviteScreen(
            householdId = openHouseholdId!!,
            onBack = { showInvite = false },
        )
        openLocationId == null -> StorageOverviewScreen(
            householdId = openHouseholdId!!,
            onBack = { openHouseholdId = null },
            onOpenLocation = { openLocationId = it },
            onOpenSearch = { showSearch = true },
            onOpenInvite = { showInvite = true },
        )
        openShelfId == null -> ShelvesScreen(
            householdId = openHouseholdId!!,
            locationId = openLocationId!!,
            onBack = { openLocationId = null },
            onOpenShelf = { openShelfId = it },
        )
        else -> ProductsScreen(
            householdId = openHouseholdId!!,
            shelfId = openShelfId!!,
            onBack = { openShelfId = null },
        )
    }
}
