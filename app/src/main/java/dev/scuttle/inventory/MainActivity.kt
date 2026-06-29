package dev.scuttle.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dev.scuttle.inventory.ui.app.AppDrawer
import dev.scuttle.inventory.ui.app.DrawerViewModel
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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
    drawerViewModel: DrawerViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    LaunchedEffect(authState.authenticated) {
        if (authState.authenticated) {
            val defaultId = drawerViewModel.getDefault()
            val target = if (defaultId != null) Routes.storage(defaultId) else Routes.HOUSEHOLDS
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            drawerViewModel.refresh()
        } else {
            navController.navigate(Routes.AUTH) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                viewModel = drawerViewModel,
                onNavigateHome = {
                    closeDrawer()
                    navController.navigate(Routes.HOUSEHOLDS) {
                        popUpTo(Routes.HOUSEHOLDS) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateHousehold = { householdId ->
                    closeDrawer()
                    navController.navigate(Routes.storage(householdId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateSettings = {
                    closeDrawer()
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
            )
        },
        gesturesEnabled = authState.authenticated,
    ) {
        NavHost(navController = navController, startDestination = Routes.AUTH) {
            composable(Routes.AUTH) {
                AuthScreen(viewModel = authViewModel)
            }

            composable(Routes.HOUSEHOLDS) {
                HouseholdsScreen(
                    onOpenHousehold = { navController.navigate(Routes.storage(it)) },
                    onOpenDrawer = openDrawer,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenDrawer = openDrawer,
                    onSignOut = { authViewModel.signOut() },
                    onOpenInvite = {
                        val id = drawerViewModel.getDefault()
                        if (id != null) navController.navigate(Routes.invite(id))
                    },
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
                    onOpenDrawer = openDrawer,
                    onOpenLocation = { navController.navigate(Routes.location(householdId, it)) },
                    onOpenSearch = { navController.navigate(Routes.search(householdId)) },
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
                    onOpenDrawer = openDrawer,
                )
            }
        }
    }
}
