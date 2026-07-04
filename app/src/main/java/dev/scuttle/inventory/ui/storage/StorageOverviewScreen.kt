package dev.scuttle.inventory.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import dev.scuttle.inventory.ui.theme.FrostCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.ui.common.ErrorRetry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorageOverviewScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenLocation: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    viewModel: StorageOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var pendingDeleteLocation by remember { mutableStateOf<LocationDto?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(householdId) { viewModel.load(householdId) }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.all_storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.storage_overview_search_cd))
                    }
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(modifier = Modifier.navigationBarsPadding(), onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.all_storage_add_location_cd))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let {
                ErrorRetry(message = it, onRetry = viewModel::refresh)
            }

            if (state.locations.isEmpty() && !state.loading) {
                Text(
                    text = stringResource(R.string.storage_overview_empty),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            state.locations.forEach { location ->
                key(location.id) {
                    // Hoist formatted string for use inside non-composable semantics block
                    val openDesc = stringResource(R.string.storage_overview_open_cd, location.name)

                    val swipeState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                pendingDeleteLocation = location
                            }
                            false // always snap back; deletion confirmed via dialog
                        },
                    )
                    SwipeToDismissBox(
                        state = swipeState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.medium,
                                    ),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        },
                    ) {
                        FrostCard(
                            onClick = { onOpenLocation(location.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = openDesc },
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = location.name, style = MaterialTheme.typography.bodyLarge)
                                Text(text = location.type, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
        } // end PullToRefreshBox
    }

    pendingDeleteLocation?.let { location ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLocation = null },
            title = { Text(stringResource(R.string.delete_dialog_location_title, location.name)) },
            text = { Text(stringResource(R.string.delete_dialog_location_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLocation(location.id)
                        pendingDeleteLocation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteLocation = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.add_storage_sheet_title), style = MaterialTheme.typography.titleLarge)

                Text(text = stringResource(R.string.add_storage_type_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STORAGE_TYPES.forEach { type ->
                        FilterChip(
                            selected = state.newType == type,
                            onClick = { viewModel.onTypeSelect(type) },
                            label = { Text(type) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = viewModel::onNewNameChange,
                        label = { Text(stringResource(R.string.add_storage_name_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            viewModel.create()
                            showAddSheet = false
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.create()
                            showAddSheet = false
                        },
                        enabled = !state.loading && state.newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }
        }
    }
}
