package dev.scuttle.inventory.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.ui.app.DrawerViewModel
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
    onOpenDrawer: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
    val state by shelvesViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { state.shelves.size })
    val currentPage = pagerState.currentPage.coerceAtMost((state.shelves.size - 1).coerceAtLeast(0))
    val currentShelfId = state.shelves.getOrNull(currentPage)?.id

    var showAddShelfSheet by remember { mutableStateOf(false) }
    var showAddProductSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track per-shelf warning state so we can roll up to location level
    var shelfWarnings by remember { mutableStateOf(mapOf<Long, Boolean>()) }

    LaunchedEffect(householdId, locationId) {
        shelvesViewModel.load(householdId, locationId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (state.deleteMode && state.selectedShelves.isNotEmpty()) {
                        Text("${state.selectedShelves.size} selected")
                    } else {
                        Text("Shelves")
                    }
                },
                navigationIcon = {
                    if (state.deleteMode) {
                        TextButton(onClick = shelvesViewModel::exitDeleteMode) { Text("Cancel") }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                if (state.selectedShelves.isEmpty()) "Delete"
                                else "Delete (${state.selectedShelves.size})"
                            )
                        }
                    } else {
                        if (state.shelves.isNotEmpty()) {
                            IconButton(onClick = shelvesViewModel::enterDeleteMode) {
                                Icon(Icons.Default.Delete, contentDescription = "Select shelves to delete")
                            }
                        }
                        IconButton(onClick = { showAddShelfSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add shelf")
                        }
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.deleteMode && currentShelfId != null) {
                FloatingActionButton(onClick = { showAddProductSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add product")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (state.shelves.isEmpty()) {
                if (!state.loading) {
                    Text(
                        text = "No shelves yet. Tap + in the top bar to add one.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = currentPage,
                ) {
                    state.shelves.forEachIndexed { index, shelf ->
                        val isSelected = if (state.deleteMode) shelf.id in state.selectedShelves
                                         else currentPage == index
                        val tabHasWarning = shelfWarnings[shelf.id] == true
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
                                    ) {
                                        Text(
                                            shelf.name,
                                            color = if (tabHasWarning && !state.deleteMode) MaterialTheme.colorScheme.error else Color.Unspecified,
                                        )
                                        if (tabHasWarning && !state.deleteMode) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                            )
                                        }
                                    }
                                }
                            },
                            icon = if (state.deleteMode && shelf.id in state.selectedShelves) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !state.deleteMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    val shelf = state.shelves[page]
                    ProductsPane(
                        householdId = householdId,
                        shelfId = shelf.id,
                        onOpenProduct = { product -> onOpenProduct(householdId, product.shelf_id, product.id) },
                        onWarningChange = { hasWarning ->
                            shelfWarnings = shelfWarnings + (shelf.id to hasWarning)
                            drawerViewModel.reportLocationWarning(locationId, shelfWarnings.values.any { it })
                        },
                    )
                }
            }
        }
    }

    if (showAddShelfSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddShelfSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Add shelf", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = shelvesViewModel::onNewNameChange,
                        label = { Text("Shelf name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
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
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
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

    LaunchedEffect(householdId, shelfId) { viewModel.load(householdId, shelfId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Add product", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = viewModel::onNewNameChange,
                        label = { Text("Product name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
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
                        Text("Add")
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                state.suggestions.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyboardController?.hide()
                                viewModel.selectSuggestion(name)
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
