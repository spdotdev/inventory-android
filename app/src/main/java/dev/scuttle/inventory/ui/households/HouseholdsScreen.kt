package dev.scuttle.inventory.ui.households

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenInvite: (householdId: Long) -> Unit = {},
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmLeaveId by remember { mutableStateOf<Long?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text("My Households") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create household")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            if (state.households.isEmpty() && !state.loading) {
                Text(
                    text = "No households yet. Tap + to create one.",
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            state.households.forEach { household ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = household.name },
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = household.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onOpenInvite(household.id) }) {
                            Icon(Icons.Default.Share, contentDescription = "Invite to ${household.name}")
                        }
                        TextButton(onClick = { confirmLeaveId = household.id }) {
                            Text("Leave", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
        } // end PullToRefreshBox
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Create household", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.newName,
                    onValueChange = viewModel::onNewNameChange,
                    label = { Text("Household name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        viewModel.create()
                        showCreateSheet = false
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.create()
                        showCreateSheet = false
                    },
                    enabled = !state.loading && state.newName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create")
                }
            }
        }
    }

    confirmLeaveId?.let { id ->
        val name = state.households.find { it.id == id }?.name ?: "this household"
        AlertDialog(
            onDismissRequest = { confirmLeaveId = null },
            title = { Text("Leave $name?") },
            text = { Text("You'll need a new invite to rejoin.") },
            confirmButton = {
                TextButton(onClick = { viewModel.leave(id); confirmLeaveId = null }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeaveId = null }) { Text("Cancel") }
            },
        )
    }
}
