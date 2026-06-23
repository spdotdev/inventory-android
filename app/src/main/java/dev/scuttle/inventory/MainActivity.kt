package dev.scuttle.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dev.scuttle.inventory.ui.auth.AuthScreen
import dev.scuttle.inventory.ui.auth.AuthViewModel
import dev.scuttle.inventory.ui.households.HouseholdsScreen
import dev.scuttle.inventory.ui.invite.InviteScreen
import dev.scuttle.inventory.ui.location.LocationDetailScreen
import dev.scuttle.inventory.ui.search.SearchScreen
import dev.scuttle.inventory.ui.settings.SettingsScreen
import dev.scuttle.inventory.ui.settings.ThemeViewModel
import dev.scuttle.inventory.ui.storage.StorageOverviewScreen
import dev.scuttle.inventory.ui.theme.InventoryTheme
import dev.scuttle.inventory.ui.theme.ThemeMode

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Activity-scoped: applied here AND mutated by SettingsScreen (passed down).
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val mode by themeViewModel.mode.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            InventoryTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InventoryNavHost(themeViewModel = themeViewModel)
                }
            }
        }
    }
}

private object Routes {
    const val AUTH = "auth"
    const val HOUSEHOLDS = "households"
    const val SETTINGS = "settings"
    const val STORAGE = "storage/{householdId}"
    const val SEARCH = "search/{householdId}"
    const val INVITE = "invite/{householdId}"
    const val LOCATION = "location/{householdId}/{locationId}"

    fun storage(householdId: Long) = "storage/$householdId"
    fun search(householdId: Long) = "search/$householdId"
    fun invite(householdId: Long) = "invite/$householdId"
    fun location(householdId: Long, locationId: Long) = "location/$householdId/$locationId"
}

@Composable
private fun InventoryNavHost(
    themeViewModel: ThemeViewModel,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsState()

    // Redirect on auth transitions, clearing the back stack each way.
    LaunchedEffect(authState.authenticated) {
        val target = if (authState.authenticated) Routes.HOUSEHOLDS else Routes.AUTH
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(viewModel = authViewModel)
        }

        composable(Routes.HOUSEHOLDS) {
            HouseholdsScreen(
                onOpenHousehold = { navController.navigate(Routes.storage(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignOut = { authViewModel.signOut() },
                themeViewModel = themeViewModel,
            )
        }

        composable(
            route = Routes.STORAGE,
            arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
        ) { entry ->
            val householdId = entry.arguments?.getLong("householdId") ?: return@composable
            StorageOverviewScreen(
                householdId = householdId,
                onBack = { navController.popBackStack() },
                onOpenLocation = { navController.navigate(Routes.location(householdId, it)) },
                onOpenSearch = { navController.navigate(Routes.search(householdId)) },
                onOpenInvite = { navController.navigate(Routes.invite(householdId)) },
            )
        }

        composable(
            route = Routes.SEARCH,
            arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
        ) { entry ->
            val householdId = entry.arguments?.getLong("householdId") ?: return@composable
            SearchScreen(householdId = householdId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.INVITE,
            arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
        ) { entry ->
            val householdId = entry.arguments?.getLong("householdId") ?: return@composable
            InviteScreen(householdId = householdId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.LOCATION,
            arguments = listOf(
                navArgument("householdId") { type = NavType.LongType },
                navArgument("locationId") { type = NavType.LongType },
            ),
        ) { entry ->
            val householdId = entry.arguments?.getLong("householdId") ?: return@composable
            val locationId = entry.arguments?.getLong("locationId") ?: return@composable
            LocationDetailScreen(
                householdId = householdId,
                locationId = locationId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
