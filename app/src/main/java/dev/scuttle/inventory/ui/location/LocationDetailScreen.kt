package dev.scuttle.inventory.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.shelfDisplayName
import dev.scuttle.inventory.ui.hierarchy.EditableRow
import dev.scuttle.inventory.ui.products.ProductsPane
import dev.scuttle.inventory.ui.products.ProductsViewModel
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import dev.scuttle.inventory.ui.theme.ShelfAvatar
import dev.scuttle.inventory.ui.theme.themeFor
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Tab as TabViewIcon

/** Size of the themed avatar shown before each shelf's LIST-mode row name. */
private val SHELF_AVATAR_SIZE = 28.dp

/** Compact colour/warning dot size in the shelf TABS strip — deliberately small and subtle. */
private val SHELF_TAB_DOT_SIZE = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    householdId: Long,
    locationId: Long,
    drawerViewModel: DrawerViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    onOpenScanner: () -> Unit = {},
    scannedCode: String? = null,
    onScannedCodeConsumed: () -> Unit = {},
    onOpenManageShelves: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
    val state by shelvesViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { state.shelves.size })
    val currentPage = pagerState.currentPage.coerceAtMost((state.shelves.size - 1).coerceAtLeast(0))
    val currentShelfId = state.shelves.getOrNull(currentPage)?.id

    // The location's own name for the top-bar title (ALSO FIX, final review): this
    // screen used to render the generic "Shelves" title always, even though the
    // location itself became renamable this branch. drawerViewModel already holds
    // every household's locations (it's what got the user here in the first
    // place), so no extra network call is needed — just fall back to the generic
    // title for the brief window before that data has loaded.
    val drawerState by drawerViewModel.state.collectAsState()
    val locationName =
        drawerState.entries
            .firstOrNull { it.id == householdId }
            ?.locations
            ?.firstOrNull { it.id == locationId }
            ?.name

    var showAddProductSheet by rememberSaveable { mutableStateOf(false) }
    var productsRefreshKey by remember { mutableIntStateOf(0) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track per-shelf warning state so we can roll up to location level
    var shelfWarnings by remember { mutableStateOf(mapOf<Long, Boolean>()) }

    LaunchedEffect(householdId, locationId) {
        shelvesViewModel.load(householdId, locationId)
    }

    // When this location's tile showed a stock warning, land on the shelf that
    // caused it instead of always page 0. One-shot (survives rotation via
    // rememberSaveable) so it never fights the user's own tab swipes afterwards.
    val warningShelfId = drawerState.warningShelfByLocation[locationId]
    var openedOnWarningShelf by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.shelves, warningShelfId) {
        if (!openedOnWarningShelf && state.shelves.isNotEmpty()) {
            openedOnWarningShelf = true
            val index = state.shelves.indexOfFirst { it.id == warningShelfId }
            if (index > 0) pagerState.scrollToPage(index)
        }
    }

    // Hosts one-shot action errors from the ProductsPane(s) below. Shelf
    // rename/reorder/delete (and their own undo snackbar) now live entirely on
    // ManageShelvesScreen, reached via the top bar's gear icon.
    val snackbarHostState = remember { SnackbarHostState() }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(locationName ?: stringResource(R.string.location_shelves_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = shelvesViewModel::toggleListView) {
                        Icon(
                            imageVector =
                                if (state.listView) {
                                    Icons.Default.TabViewIcon
                                } else {
                                    Icons.AutoMirrored.Filled.ViewList
                                },
                            contentDescription = stringResource(R.string.location_view_toggle_cd),
                        )
                    }
                    // Shelf management (rename/reorder/delete/add) moved off this
                    // screen entirely onto its own always-on ManageShelvesScreen —
                    // see CLAUDE.md's "Editing the hierarchy" paragraph. Gated on
                    // canRestructure the same way the old pencil was, so a Member
                    // never sees an affordance the server would 403.
                    if (state.canRestructure) {
                        IconButton(onClick = { onOpenManageShelves(householdId, locationId) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.shelves_manage_cd),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (currentShelfId != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = onOpenScanner) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.location_scan_cd),
                        )
                    }
                    FloatingActionButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = { showAddProductSheet = true },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.location_add_product_cd))
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = {
                shelvesViewModel.refresh()
                productsRefreshKey++
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
            ) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.errorRes?.let {
                    ErrorRetry(
                        stringResource(it),
                        onRetry = shelvesViewModel::refresh,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                if (state.shelves.isEmpty()) {
                    // Suppress "no shelves yet" on a failed load — the error line above
                    // already explains it; showing both reads as a false empty (W7).
                    if (!state.loading && state.errorRes == null) {
                        Text(
                            text = stringResource(R.string.location_no_shelves),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (state.listView) {
                    // This is now always the PLAIN (non-edit) alternative, full-name
                    // shelf selector — tapping a row drills back into the tabs+pager
                    // view centered on that shelf. Rename/reorder/delete/add live
                    // entirely on ManageShelvesScreen now (see the gear icon above),
                    // so EditableRow is used here with editMode permanently false —
                    // it never renders the checkbox/pencil/reorder affordances.
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.shelves, key = { _, shelf -> shelf.id }) { index, shelf ->
                            EditableRow(
                                name = shelfDisplayName(shelf),
                                editMode = false,
                                isSystem = shelf.is_system,
                                selected = false,
                                canMoveUp = false,
                                canMoveDown = false,
                                actionsEnabled = !state.loading,
                                // A stable handle a driving test can wait on instead of
                                // racing this row's name text against the plain
                                // tab/pager rendering it replaces (same shape as
                                // StorageOverviewScreen's location-row-<id>).
                                modifier = Modifier.testTag("shelf-row-${shelf.id}"),
                                leadingIcon = {
                                    ShelfAvatar(
                                        shelfId = shelf.id,
                                        size = SHELF_AVATAR_SIZE,
                                        colorKey = shelf.color,
                                        iconKey = shelf.icon,
                                    )
                                },
                                onClick = {
                                    scope.launch { pagerState.scrollToPage(index) }
                                    shelvesViewModel.toggleListView()
                                },
                                onRename = {},
                                onMoveUp = {},
                                onMoveDown = {},
                            )
                        }
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = currentPage,
                    ) {
                        state.shelves.forEachIndexed { index, shelf ->
                            val tabHasWarning = shelfWarnings[shelf.id] == true
                            // The warning is conveyed visually by red text + a dot; give the
                            // text row a content description so it isn't color-only (WCAG
                            // 1.4.1) and TalkBack announces "<shelf>, has missing items" (W9).
                            val warningCd =
                                if (tabHasWarning) {
                                    stringResource(R.string.location_shelf_missing_cd, shelfDisplayName(shelf))
                                } else {
                                    null
                                }
                            Tab(
                                selected = currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier =
                                            if (warningCd != null) {
                                                Modifier.clearAndSetSemantics { contentDescription = warningCd }
                                            } else {
                                                Modifier
                                            },
                                    ) {
                                        // A compact colour dot only when a theme colour was
                                        // actually chosen — kept deliberately subtle (no icon,
                                        // no background wash) so the tab strip stays plain for
                                        // the common untouched case, per CLAUDE.md's design bar.
                                        if (shelf.color != null) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(SHELF_TAB_DOT_SIZE)
                                                        .background(
                                                            themeFor(shelf.id, shelf.color).accent,
                                                            CircleShape,
                                                        ),
                                            )
                                        }
                                        Text(
                                            shelfDisplayName(shelf),
                                            color =
                                                if (tabHasWarning) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    Color.Unspecified
                                                },
                                        )
                                        if (tabHasWarning) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(SHELF_TAB_DOT_SIZE)
                                                        .background(MaterialTheme.colorScheme.error, CircleShape),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) { page ->
                        val shelf = state.shelves[page]
                        ProductsPane(
                            householdId = householdId,
                            shelfId = shelf.id,
                            snackbarHostState = snackbarHostState,
                            onOpenProduct = { product -> onOpenProduct(householdId, product.shelf_id, product.id) },
                            onWarningChange = { hasWarning ->
                                shelfWarnings = shelfWarnings + (shelf.id to hasWarning)
                                drawerViewModel.reportLocationWarning(locationId, shelfWarnings.values.any { it })
                            },
                            refreshKey = productsRefreshKey,
                        )
                    }
                }
            }
        } // end PullToRefreshBox
    }

    // Shelf rename/reorder/delete (strategy dialog + undo snackbar) and add-shelf
    // moved off this screen entirely onto ManageShelvesScreen — see the gear icon
    // in the top bar above and CLAUDE.md's "Editing the hierarchy" paragraph.

    if (scannedCode != null && currentShelfId != null) {
        val activePaneViewModel: ProductsViewModel = hiltViewModel(key = "products-$currentShelfId")
        LaunchedEffect(scannedCode) {
            activePaneViewModel.onBarcodeScanned(scannedCode)
            onScannedCodeConsumed()
        }
    }

    if (showAddProductSheet && currentShelfId != null) {
        AddProductSheet(
            householdId = householdId,
            shelfId = currentShelfId,
            onDismiss = { showAddProductSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProductSheet(
    householdId: Long,
    shelfId: Long,
    onDismiss: () -> Unit,
    viewModel: ProductsViewModel = hiltViewModel(key = "products-$shelfId"),
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_product_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = viewModel::onNewNameChange,
                        label = { Text(stringResource(R.string.add_product_field_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                keyboardController?.hide()
                                viewModel.create()
                                onDismiss()
                            }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.create()
                            onDismiss()
                        },
                        enabled = !state.loading && state.newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                state.suggestions.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    viewModel.selectSuggestion(name)
                                }.padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
