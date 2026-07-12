package dev.scuttle.inventory.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.app.DrawerViewModel
import dev.scuttle.inventory.ui.common.LiveStatusText
import dev.scuttle.inventory.ui.products.ProductsPane
import dev.scuttle.inventory.ui.products.ProductsViewModel
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.launch

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
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
    val state by shelvesViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { state.shelves.size })
    val currentPage = pagerState.currentPage.coerceAtMost((state.shelves.size - 1).coerceAtLeast(0))
    val currentShelfId = state.shelves.getOrNull(currentPage)?.id

    var showAddShelfSheet by rememberSaveable { mutableStateOf(false) }
    var showAddProductSheet by rememberSaveable { mutableStateOf(false) }
    var productsRefreshKey by remember { mutableIntStateOf(0) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track per-shelf warning state so we can roll up to location level
    var shelfWarnings by remember { mutableStateOf(mapOf<Long, Boolean>()) }

    LaunchedEffect(householdId, locationId) {
        shelvesViewModel.load(householdId, locationId)
    }

    // Hosts one-shot action errors from the ProductsPane(s) below.
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
                    if (state.deleteMode && state.selectedShelves.isNotEmpty()) {
                        Text(stringResource(R.string.location_selected_count, state.selectedShelves.size))
                    } else {
                        Text(stringResource(R.string.location_shelves_title))
                    }
                },
                navigationIcon = {
                    if (state.deleteMode) {
                        TextButton(
                            onClick = shelvesViewModel::exitDeleteMode,
                        ) { Text(stringResource(R.string.action_cancel)) }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (state.deleteMode) {
                        Button(
                            onClick = shelvesViewModel::deleteSelected,
                            enabled = state.selectedShelves.isNotEmpty() && !state.loading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                if (state.selectedShelves.isEmpty()) {
                                    stringResource(R.string.location_delete_button)
                                } else {
                                    stringResource(R.string.location_delete_count_button, state.selectedShelves.size)
                                },
                            )
                        }
                    } else {
                        if (state.shelves.isNotEmpty()) {
                            IconButton(onClick = shelvesViewModel::enterDeleteMode) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.location_delete_shelves_cd),
                                )
                            }
                        }
                        IconButton(onClick = { showAddShelfSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.location_add_shelf_cd))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.deleteMode && currentShelfId != null) {
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

                state.error?.let {
                    LiveStatusText(it, modifier = Modifier.padding(16.dp))
                }

                if (state.shelves.isEmpty()) {
                    // Suppress "no shelves yet" on a failed load — the error line above
                    // already explains it; showing both reads as a false empty (W7).
                    if (!state.loading && state.error == null) {
                        Text(
                            text = stringResource(R.string.location_no_shelves),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = currentPage,
                    ) {
                        state.shelves.forEachIndexed { index, shelf ->
                            val isSelected =
                                if (state.deleteMode) {
                                    shelf.id in state.selectedShelves
                                } else {
                                    currentPage == index
                                }
                            val tabHasWarning = shelfWarnings[shelf.id] == true
                            // The warning is conveyed visually by red text + a dot; give the
                            // text row a content description so it isn't color-only (WCAG
                            // 1.4.1) and TalkBack announces "<shelf>, has missing items" (W9).
                            val warningCd =
                                if (tabHasWarning && !state.deleteMode) {
                                    stringResource(R.string.location_shelf_missing_cd, shelf.name)
                                } else {
                                    null
                                }
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    if (state.deleteMode) {
                                        shelvesViewModel.toggleShelfSelection(shelf.id)
                                    } else {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                },
                                text = {
                                    if (state.deleteMode && shelf.id in state.selectedShelves) {
                                        Text(shelf.name)
                                    } else {
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
                                            Text(
                                                shelf.name,
                                                color =
                                                    if (tabHasWarning && !state.deleteMode) {
                                                        MaterialTheme.colorScheme.error
                                                    } else {
                                                        Color.Unspecified
                                                    },
                                            )
                                            if (tabHasWarning && !state.deleteMode) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .size(6.dp)
                                                            .background(MaterialTheme.colorScheme.error, CircleShape),
                                                )
                                            }
                                        }
                                    }
                                },
                                icon =
                                    if (state.deleteMode && shelf.id in state.selectedShelves) {
                                        {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !state.deleteMode,
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

    if (showAddShelfSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddShelfSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.add_shelf_sheet_title), style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = shelvesViewModel::onNewNameChange,
                        label = { Text(stringResource(R.string.add_shelf_field_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                keyboardController?.hide()
                                shelvesViewModel.create()
                                showAddShelfSheet = false
                            }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            shelvesViewModel.create()
                            showAddShelfSheet = false
                        },
                        enabled = !state.loading && state.newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
        }
    }

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
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
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
