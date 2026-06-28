package dev.scuttle.inventory.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorageOverviewScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenLocation: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenInvite: () -> Unit = {},
    viewModel: StorageOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(householdId) { viewModel.load(householdId) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search products")
                    }
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add storage location")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            TextButton(onClick = onOpenInvite) {
                Text("Invite members")
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            if (state.locations.isEmpty() && !state.loading) {
                Text(
                    text = "No storage locations yet. Tap + to add a fridge, freezer or pantry.",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            state.locations.forEach { location ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Open ${location.name}" }
                        .clickable { onOpenLocation(location.id) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = location.name, style = MaterialTheme.typography.bodyLarge)
                        Text(text = location.type, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
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
                Text(text = "Add storage", style = MaterialTheme.typography.titleLarge)

                Text(text = "Type")
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
                        label = { Text("Name") },
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
                        Text("Add")
                    }
                }
            }
        }
    }
}
