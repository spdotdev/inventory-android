package dev.scuttle.inventory.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.SortMenu
import dev.scuttle.inventory.ui.theme.FrostCard

@Composable
fun SearchScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    // Set by the scan-to-lookup flow (bottom-bar Scan tab, mode=lookup): the
    // scanned code, pre-filled as the query and run immediately rather than
    // landing on an empty search box — see MainActivity's ScanDeliveryAction.
    initialQuery: String? = null,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(householdId) {
        viewModel.setHousehold(householdId)
        // Runs in the same effect as setHousehold, after it: setHousehold on a
        // new household resets state to a fresh SearchUiState(), which would
        // wipe an initialQuery applied any earlier.
        if (!initialQuery.isNullOrBlank()) {
            viewModel.searchFor(initialQuery)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.search_back_button))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.search_field_label)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    autoCorrect = false,
                    imeAction = ImeAction.Search,
                ),
            keyboardActions =
                KeyboardActions(
                    onSearch = { keyboardController?.hide() },
                ),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("search_field"),
        )

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let {
            ErrorRetry(it, onRetry = viewModel::search)
        }

        if (state.query.isNotBlank() && state.results.isEmpty() && !state.loading) {
            Text(text = stringResource(R.string.search_no_results))
        }

        if (state.results.isNotEmpty()) {
            SortMenu(current = state.sort, onSelect = viewModel::setSort)
        }

        state.sortedResults.forEach { result ->
            val hhId = result.household_id
            val shelfId = result.shelf_id
            val content: @Composable () -> Unit = {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = result.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = result.path.ifBlank { "${result.location} › ${result.shelf}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.search_result_qty, result.quantity),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (hhId != null && shelfId != null) {
                FrostCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenProduct(hhId, shelfId, result.id) },
                    content = { content() },
                )
            } else {
                FrostCard(modifier = Modifier.fillMaxWidth(), content = { content() })
            }
        }
    }
}
