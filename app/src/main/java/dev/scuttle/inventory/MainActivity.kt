package dev.scuttle.inventory

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dev.scuttle.inventory.data.realtime.LiveUpdates
import dev.scuttle.inventory.data.settings.SharedPrefsLanguageStore
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.auth.AuthScreen
import dev.scuttle.inventory.ui.auth.AuthViewModel
import dev.scuttle.inventory.ui.auth.ForgotPasswordScreen
import dev.scuttle.inventory.ui.dashboard.DashboardScreen
import dev.scuttle.inventory.ui.home.AllStoragesScreen
import dev.scuttle.inventory.ui.households.HouseholdsScreen
import dev.scuttle.inventory.ui.invite.InviteScreen
import dev.scuttle.inventory.ui.location.LocationDetailScreen
import dev.scuttle.inventory.ui.missing.MissingItemsScreen
import dev.scuttle.inventory.ui.products.ProductDetailScreen
import dev.scuttle.inventory.ui.scanner.ScannerScreen
import dev.scuttle.inventory.ui.search.SearchScreen
import dev.scuttle.inventory.ui.settings.SettingsScreen
import dev.scuttle.inventory.ui.settings.ThemeViewModel
import dev.scuttle.inventory.ui.storage.StorageOverviewScreen
import dev.scuttle.inventory.ui.theme.InventoryTheme
import dev.scuttle.inventory.ui.theme.ThemeMode
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Live updates (Q-3): connected while this (single) activity is started —
    // i.e. exactly while the app is in the foreground.
    @Inject
    lateinit var liveUpdates: LiveUpdates

    override fun onStart() {
        super.onStart()
        liveUpdates.setForeground(true)
    }

    override fun onStop() {
        liveUpdates.setForeground(false)
        super.onStop()
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = SharedPrefsLanguageStore(newBase).get()
        val locale = java.util.Locale(lang.tag)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        liveUpdates.start()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val mode by themeViewModel.mode.collectAsState()
            val dark =
                when (mode) {
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

private data class BottomTab(
    val key: String,
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private object Routes {
    const val AUTH = "auth"
    const val FORGOT_PASSWORD = "forgot-password"
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val DASHBOARD = "dashboard"
    const val HOUSEHOLDS = "households"
    const val SETTINGS = "settings"
    const val STORAGE = "storage/{householdId}"
    const val SEARCH = "search/{householdId}"
    const val INVITE = "invite/{householdId}/{householdName}"
    const val LOCATION = "location/{householdId}/{locationId}"
    const val PRODUCT_DETAIL = "product-detail/{householdId}/{shelfId}/{productId}"
    const val MISSING_ITEMS = "missing-items"

    fun storage(householdId: Long) = "storage/$householdId"

    fun search(householdId: Long) = "search/$householdId"

    fun invite(
        householdId: Long,
        householdName: String,
    ) = "invite/$householdId/${java.net.URLEncoder.encode(householdName, "UTF-8")}"

    fun location(
        householdId: Long,
        locationId: Long,
    ) = "location/$householdId/$locationId"

    fun productDetail(
        householdId: Long,
        shelfId: Long,
        productId: Long,
    ) = "product-detail/$householdId/$shelfId/$productId"
}

/** Where an auth-state change should send the user. */
enum class AuthRedirect { TO_DASHBOARD, TO_AUTH }

/**
 * Decides the post-auth redirect from the previous and current authenticated flags.
 * Returns null when there is **no real transition** — process-death restore
 * (`previous == current`) or a cold start that's still unauthenticated
 * (`previous == null && !current`, where AUTH is already the start destination) —
 * so the restored/initial back stack is left intact. `previous == null` marks the
 * first composition. Pure + framework-free so it's unit-testable.
 */
fun authRedirectFor(
    previous: Boolean?,
    current: Boolean,
): AuthRedirect? =
    when {
        previous == current -> null
        previous == null && !current -> null
        current -> AuthRedirect.TO_DASHBOARD
        else -> AuthRedirect.TO_AUTH
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryNavHost(
    themeViewModel: ThemeViewModel,
    authViewModel: AuthViewModel = hiltViewModel(),
    drawerViewModel: DrawerViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsState()

    // Redirect only on an actual auth *transition* (login / logout), never on a bare
    // `authenticated == true`. On process-death restore the NavController rehydrates its
    // own back stack; re-running the stack-clearing navigate() here would throw the user
    // back to the dashboard from wherever they were. `lastAuth` is saved across death, so
    // on restore it already equals the current value and authRedirectFor() returns null.
    // The decision is a pure function (authRedirectFor) so it's unit-testable.
    var lastAuth by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(authState.authenticated) {
        val current = authState.authenticated
        val redirect = authRedirectFor(previous = lastAuth, current = current)
        lastAuth = current

        if (current) drawerViewModel.refresh()
        when (redirect) {
            AuthRedirect.TO_DASHBOARD ->
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            AuthRedirect.TO_AUTH ->
                navController.navigate(Routes.AUTH) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            null -> Unit // no transition — leave the restored back stack intact
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerUi by drawerViewModel.state.collectAsState()
    var showHouseholdPicker by remember { mutableStateOf(false) }
    val bottomTabs =
        listOf(
            BottomTab("dashboard", Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.SpaceDashboard),
            BottomTab("home", Routes.HOME, R.string.nav_storage, Icons.Filled.Home),
            BottomTab("households", Routes.HOUSEHOLDS, R.string.nav_households, Icons.Filled.People),
            BottomTab("missing-items", Routes.MISSING_ITEMS, R.string.nav_missing_items, Icons.Filled.Warning),
            BottomTab("search", Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
        )
    val onOpenSettings: () -> Unit = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (tab.key == "search") {
                                    val entries = drawerUi.entries
                                    when {
                                        entries.size == 1 ->
                                            navController.navigate(Routes.search(entries.first().id)) {
                                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        entries.size > 1 -> showHouseholdPicker = true
                                        else -> Unit
                                    }
                                } else {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (tab.key == "missing-items" && drawerUi.missingItemCount > 0) {
                                    BadgedBox(badge = { Badge { Text("${drawerUi.missingItemCount}") } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag("bottom-nav-${tab.key}"),
                        )
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        if (showHouseholdPicker) {
            ModalBottomSheet(onDismissRequest = { showHouseholdPicker = false }) {
                Text(
                    stringResource(R.string.search_choose_household_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                drawerUi.entries.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry.name) },
                        selected = false,
                        onClick = {
                            showHouseholdPicker = false
                            navController.navigate(Routes.search(entry.id)) {
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("household-picker-${entry.name}"),
                    )
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = Routes.AUTH,
            modifier = Modifier.padding(scaffoldPadding),
        ) {
            composable(Routes.AUTH) {
                AuthScreen(
                    viewModel = authViewModel,
                    onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                )
            }

            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.HOME) {
                AllStoragesScreen(
                    viewModel = drawerViewModel,
                    onOpenSettings = onOpenSettings,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenStorage = { hhId ->
                        navController.navigate(Routes.storage(hhId))
                    },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenSettings = onOpenSettings,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenAllStorage = {
                        navController.navigate(Routes.HOME) { launchSingleTop = true }
                    },
                    onOpenSearch = { hhId ->
                        navController.navigate(Routes.search(hhId)) { launchSingleTop = true }
                    },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS) },
                    onOpenMissingItems = { navController.navigate(Routes.MISSING_ITEMS) { launchSingleTop = true } },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignOut = { authViewModel.signOut() },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS) },
                    themeViewModel = themeViewModel,
                )
            }

            composable(Routes.HOUSEHOLDS) {
                HouseholdsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings,
                    onOpenInvite = { id, name -> navController.navigate(Routes.invite(id, name)) },
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
                )
            }

            composable(
                route = Routes.SEARCH,
                arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                SearchScreen(
                    householdId = householdId,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings,
                    onOpenProduct = { hhId, shelfId, productId ->
                        navController.navigate(Routes.productDetail(hhId, shelfId, productId))
                    },
                )
            }

            composable(
                route = Routes.INVITE,
                arguments =
                    listOf(
                        navArgument("householdId") { type = NavType.LongType },
                        navArgument("householdName") { type = NavType.StringType },
                    ),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                val householdName = entry.arguments?.getString("householdName") ?: ""
                InviteScreen(
                    householdId = householdId,
                    storageName = householdName,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.LOCATION,
                arguments =
                    listOf(
                        navArgument("householdId") { type = NavType.LongType },
                        navArgument("locationId") { type = NavType.LongType },
                    ),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                val locationId = entry.arguments?.getLong("locationId") ?: return@composable
                val scannedCode by entry.savedStateHandle
                    .getStateFlow<String?>("scanned_code", null)
                    .collectAsState()
                LocationDetailScreen(
                    householdId = householdId,
                    locationId = locationId,
                    drawerViewModel = drawerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { hhId, shelfId, productId ->
                        navController.navigate(Routes.productDetail(hhId, shelfId, productId))
                    },
                    onOpenScanner = { navController.navigate(Routes.SCANNER) },
                    scannedCode = scannedCode,
                    onScannedCodeConsumed = { entry.savedStateHandle["scanned_code"] = null },
                )
            }

            composable(Routes.MISSING_ITEMS) {
                MissingItemsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                )
            }

            composable(Routes.SCANNER) {
                ScannerScreen(
                    onScanned = { code ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("scanned_code", code)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PRODUCT_DETAIL,
                arguments =
                    listOf(
                        navArgument("householdId") { type = NavType.LongType },
                        navArgument("shelfId") { type = NavType.LongType },
                        navArgument("productId") { type = NavType.LongType },
                    ),
            ) {
                ProductDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
