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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavOptionsBuilder
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
import dev.scuttle.inventory.ui.households.HouseholdEditScreen
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
    // Where a tap on this tab actually navigates. Defaults to [route] — the plain
    // "this tab IS this destination" case every tab but Scan fits. Kept distinct
    // from [route] (which is also the pattern matched against currentRoute to
    // decide selection/bottom-bar-visibility) because Scan's destination is
    // parameterized (Routes.SCANNER = "scanner?mode={mode}"): [route] has to stay
    // the bare pattern for that match to work, while the tap needs a concrete
    // `mode` value baked in.
    val navigateTo: String = route,
)

/**
 * Which caller opened the scanner, carried explicitly as a route argument rather
 * than inferred from the back stack. Before this, [Routes.SCANNER] had exactly one
 * delivery contract — write the code to the previous entry's savedStateHandle and
 * pop — which only makes sense when a shelf screen (LocationDetailScreen) is that
 * previous entry. Once the bottom-bar Scan tab started opening the same route with
 * Dashboard/whatever-tab as the previous entry, that contract silently broke: nothing
 * there reads `scanned_code`, so a bottom-bar scan did nothing. Making the caller's
 * intent an explicit argument (ADD vs LOOKUP) lets [scanDeliveryActionFor] give each
 * caller its own, correct behavior instead of guessing from the stack shape.
 */
enum class ScannerMode(
    val argValue: String,
) {
    /** Opened from a shelf screen: deliver the code back via savedStateHandle. */
    ADD("add"),

    /** Opened from the bottom bar, with no shelf context: hand the code to Search. */
    LOOKUP("lookup"),
    ;

    companion object {
        /** Unrecognized or missing values fall back to ADD, the pre-existing behavior
         * for the route's only caller before LOOKUP existed. */
        fun from(argValue: String?): ScannerMode = entries.firstOrNull { it.argValue == argValue } ?: ADD
    }
}

/** What a freshly-scanned barcode should do next, decided purely from [ScannerMode] — no
 * NavController involved, so the ADD/LOOKUP branch is unit-testable on its own (see
 * ScanDeliveryActionTest). Mirrors [authRedirectFor]'s split between pure decision and
 * NavController side effect below.
 */
sealed interface ScanDeliveryAction {
    /** ADD: deliver [code] to the caller's savedStateHandle and pop back to it — the
     * "add this to the shelf I came from" contract LocationDetailScreen expects.
     */
    data class DeliverToCaller(
        val code: String,
    ) : ScanDeliveryAction

    /** LOOKUP: navigate to Search with [code] as the pre-filled, already-run query —
     * "do I have this, and where did I put it" (the spec's role for Search), and the
     * only sensible outcome when there's no shelf to add to.
     */
    data class NavigateToSearch(
        val code: String,
    ) : ScanDeliveryAction
}

fun scanDeliveryActionFor(
    mode: ScannerMode,
    code: String,
): ScanDeliveryAction =
    when (mode) {
        ScannerMode.ADD -> ScanDeliveryAction.DeliverToCaller(code)
        ScannerMode.LOOKUP -> ScanDeliveryAction.NavigateToSearch(code)
    }

private object Routes {
    const val AUTH = "auth"
    const val FORGOT_PASSWORD = "forgot-password"
    const val HOME = "home"
    const val SCANNER = "scanner?mode={mode}"
    const val DASHBOARD = "dashboard"
    const val HOUSEHOLDS = "households"
    const val HOUSEHOLD_EDIT = "household-edit/{householdId}"
    const val SETTINGS = "settings"
    const val STORAGE = "storage/{householdId}"
    const val SEARCH = "search/{householdId}?query={query}"
    const val INVITE = "invite/{householdId}/{householdName}"
    const val LOCATION = "location/{householdId}/{locationId}"
    const val PRODUCT_DETAIL = "product-detail/{householdId}/{shelfId}/{productId}"
    const val MISSING_ITEMS = "missing-items"

    fun householdEdit(householdId: Long) = "household-edit/$householdId"

    fun storage(householdId: Long) = "storage/$householdId"

    fun scanner(mode: ScannerMode) = "scanner?mode=${mode.argValue}"

    /**
     * [query] pre-fills and immediately runs a search (the scan-to-lookup flow) —
     * left null for every other caller, which just opens Search on an empty query
     * exactly as before this existed.
     */
    fun search(
        householdId: Long,
        query: String? = null,
    ) = "search/$householdId" + if (query != null) "?query=${java.net.URLEncoder.encode(query, "UTF-8")}" else ""

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

/**
 * Nav options shared by every unparameterized bottom-tab destination (and any other
 * call site that jumps to one, e.g. "Manage households" from Settings): pop back to
 * DASHBOARD and don't stack duplicates.
 *
 * Deliberately NOT `popUpTo(saveState = true)` / `restoreState = true`: all five bottom
 * tabs are flat single screens whose data lives in the singleton HierarchyStore, so
 * save/restore would only buy back scroll position / search text — but saveState saves
 * the ENTIRE back-stack chunk stacked above a tab root (e.g. Settings, StorageOverview,
 * Search), keyed by that chunk's deepest destination. A later navigate() to the same tab
 * with restoreState = true then replays that whole chunk instead of pushing a fresh tab
 * screen, so a tab tap can land on a non-tab screen (bottom bar disappears) or do nothing
 * at all (if the chunk being restored is the screen already showing). Not used for
 * SEARCH either — see the comments at its navigate() call sites.
 */
private val tabNavOptions: NavOptionsBuilder.() -> Unit = {
    popUpTo(Routes.DASHBOARD)
    launchSingleTop = true
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
    val bottomTabs =
        listOf(
            BottomTab("dashboard", Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.SpaceDashboard),
            BottomTab("home", Routes.HOME, R.string.nav_storage, Icons.Filled.Home),
            // The centre slot is the primary-ACTION slot. Scanning is a weekly
            // grocery-trip action; search is an occasional "where did I put it",
            // and it already has a top-bar icon. Opened from here (no shelf
            // context), the scan resolves to a Search lookup — see ScannerMode.
            BottomTab(
                "scanner",
                Routes.SCANNER,
                R.string.nav_scan,
                Icons.Filled.QrCodeScanner,
                navigateTo = Routes.scanner(ScannerMode.LOOKUP),
            ),
            BottomTab("missing-items", Routes.MISSING_ITEMS, R.string.nav_missing_items, Icons.Filled.Warning),
            // Not "Settings": it now holds households, join/invite and account.
            BottomTab("more", Routes.SETTINGS, R.string.nav_more, Icons.Filled.MoreHoriz),
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
                            onClick = { navController.navigate(tab.navigateTo, tabNavOptions) },
                            icon = {
                                if (tab.key == "missing-items" && drawerUi.missingItemCount > 0) {
                                    BadgedBox(badge = { Badge { Text("${drawerUi.missingItemCount}") } }) {
                                        Icon(
                                            tab.icon,
                                            contentDescription =
                                                stringResource(
                                                    R.string.drawer_missing_items_count,
                                                    drawerUi.missingItemCount,
                                                ),
                                        )
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
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenStorage = { hhId ->
                        navController.navigate(Routes.storage(hhId))
                    },
                    onOpenSearch = { hhId ->
                        navController.navigate(Routes.search(hhId)) { launchSingleTop = true }
                    },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenAllStorage = {
                        navController.navigate(Routes.HOME) { launchSingleTop = true }
                    },
                    onOpenSearch = { hhId ->
                        navController.navigate(Routes.search(hhId)) { launchSingleTop = true }
                    },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS, tabNavOptions) },
                    onOpenMissingItems = { navController.navigate(Routes.MISSING_ITEMS) { launchSingleTop = true } },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignOut = { authViewModel.signOut() },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS, tabNavOptions) },
                    themeViewModel = themeViewModel,
                )
            }

            composable(Routes.HOUSEHOLDS) {
                HouseholdsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings,
                    onOpenInvite = { id, name -> navController.navigate(Routes.invite(id, name)) },
                    onEditHousehold = { id -> navController.navigate(Routes.householdEdit(id)) },
                )
            }

            composable(
                route = Routes.HOUSEHOLD_EDIT,
                arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                HouseholdEditScreen(
                    householdId = householdId,
                    onBack = { navController.popBackStack() },
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
                arguments =
                    listOf(
                        navArgument("householdId") { type = NavType.LongType },
                        navArgument("query") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                SearchScreen(
                    householdId = householdId,
                    initialQuery = entry.arguments?.getString("query"),
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
                    onOpenScanner = { navController.navigate(Routes.scanner(ScannerMode.ADD)) },
                    scannedCode = scannedCode,
                    onScannedCodeConsumed = { entry.savedStateHandle["scanned_code"] = null },
                )
            }

            composable(Routes.MISSING_ITEMS) {
                MissingItemsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    firstHouseholdId = drawerUi.entries.firstOrNull()?.id,
                    onOpenSearch = { hhId ->
                        navController.navigate(Routes.search(hhId)) { launchSingleTop = true }
                    },
                )
            }

            composable(
                route = Routes.SCANNER,
                arguments =
                    listOf(
                        navArgument("mode") {
                            type = NavType.StringType
                            defaultValue = ScannerMode.ADD.argValue
                        },
                    ),
            ) { entry ->
                val mode = ScannerMode.from(entry.arguments?.getString("mode"))
                ScannerScreen(
                    onScanned = { code ->
                        when (val action = scanDeliveryActionFor(mode, code)) {
                            is ScanDeliveryAction.DeliverToCaller -> {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("scanned_code", action.code)
                                navController.popBackStack()
                            }
                            is ScanDeliveryAction.NavigateToSearch -> {
                                val householdId = drawerUi.entries.firstOrNull()?.id
                                if (householdId != null) {
                                    navController.navigate(Routes.search(householdId, action.code)) {
                                        popUpTo(Routes.SCANNER) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                } else {
                                    // Household-less account: nothing to look up in.
                                    // Back out of the scanner instead of stranding the
                                    // user on the camera.
                                    navController.popBackStack()
                                }
                            }
                        }
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
