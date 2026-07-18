package dev.scuttle.inventory.ui.households

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.theme.FrostCard
import dev.scuttle.inventory.ui.theme.HouseholdAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenInvite: (householdId: Long, householdName: String) -> Unit = { _, _ -> },
    onEditHousehold: (householdId: Long) -> Unit = {},
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(
                        stringResource(R.string.households_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    if (state.editMode) {
                        TextButton(onClick = viewModel::exitEditMode) { Text(stringResource(R.string.action_cancel)) }
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
                    if (!state.editMode) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                        if (state.households.isNotEmpty()) {
                            IconButton(onClick = viewModel::enterEditMode) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.households_edit_cd),
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.editMode) {
                FloatingActionButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = { showCreateSheet = true },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.households_create_fab_cd))
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                state.errorRes?.let {
                    ErrorRetry(stringResource(it), onRetry = viewModel::refresh)
                }

                if (state.households.isEmpty() && !state.loading) {
                    Text(
                        text = stringResource(R.string.households_empty),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }

                state.households.forEach { household ->
                    // Only edit mode makes the row navigate — outside it, the row is
                    // inert except for the share/invite icon below, same as before.
                    val onRowClick: (() -> Unit)? =
                        if (state.editMode) {
                            { onEditHousehold(household.id) }
                        } else {
                            null
                        }
                    FrostCard(
                        onClick = onRowClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = household.name },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HouseholdAvatar(
                                householdId = household.id,
                                colorKey = household.color,
                                iconKey = household.icon,
                            )
                            Text(
                                text = household.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onOpenInvite(household.id, household.name) }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.households_invite_cd, household.name),
                                )
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_create_household),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = state.newName,
                    onValueChange = viewModel::onNewNameChange,
                    label = { Text(stringResource(R.string.households_field_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(onDone = {
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
                    Text(stringResource(R.string.households_create_button))
                }
            }
        }
    }
}
