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
import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.SortMenu
import dev.scuttle.inventory.ui.theme.FrostCard

/** M9: content alpha for a non-tappable search result — see the muted-caption comment below. */
private const val MUTED_ALPHA = 0.5f
private const val FULL_ALPHA = 1f

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
    // Scan-originated zero-result CTA (GAP-5 H6): "Add a product with this
    // barcode" hands the household + scanned code back to the caller, which
    // routes to a place a product can be created carrying that code.
    onAddProductForCode: (householdId: Long, code: String) -> Unit = { _, _ -> },
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

    // Only steal focus (and pop the keyboard) on a genuinely fresh screen — an
    // empty query is the only reliable local signal for that, since the same
    // back-stack entry (and its ViewModel/state) survives a push-to-ProductDetail
    // -then-back round trip; re-focusing on every recomposition would also
    // re-open the keyboard over an already-populated results list (GAP-5 H7).
    LaunchedEffect(Unit) {
        if (state.query.isBlank()) {
            focusRequester.requestFocus()
        }
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

        state.errorRes?.let {
            ErrorRetry(stringResource(it), onRetry = viewModel::search)
        }

        if (state.query.isNotBlank() && state.results.isEmpty() && !state.loading) {
            Text(text = stringResource(R.string.search_no_results))
            // Only for a scan-originated miss (GAP-5 H6): the scanner turned up
            // nothing to look up, so offer to create a product carrying that same
            // code instead of leaving the user at a dead end.
            if (state.scanOriginated) {
                TextButton(onClick = { onAddProductForCode(householdId, state.query.trim()) }) {
                    Text(stringResource(R.string.search_no_results_add_product_cta))
                }
            }
        }

        if (state.results.isNotEmpty()) {
            SortMenu(current = state.sort, onSelect = viewModel::setSort)
        }

        state.sortedResults.forEach { result ->
            val hhId = result.household_id
            val shelfId = result.shelf_id
            // M9: a result missing household_id/shelf_id gets no onClick (there's
            // nowhere to route it), but used to render with identical styling to a
            // tappable card — nothing signaled "this one doesn't do anything" until
            // you actually tapped it. Reduced content alpha + an explicit caption
            // make the non-tappable state visually obvious at a glance.
            val isTappable = hhId != null && shelfId != null
            val contentAlpha = if (isTappable) FULL_ALPHA else MUTED_ALPHA
            val content: @Composable () -> Unit = {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    )
                    // GAP5-L2: an all-blank location AND shelf used to fall through to
                    // "${location} › ${shelf}" -> a bare " › " with nothing on either
                    // side. Reuse the same "Location unavailable" caption the
                    // !isTappable branch below already shows (M9), rather than a raw
                    // separator with no content.
                    val pathText =
                        searchResultPathText(
                            result = result,
                            unsortedShelfLabel = stringResource(R.string.shelf_unsorted),
                            locationUnavailableLabel = stringResource(R.string.search_result_location_unavailable),
                        )
                    Text(
                        text = pathText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    )
                    Text(
                        text = stringResource(R.string.search_result_qty, result.quantity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    )
                    if (!isTappable) {
                        Text(
                            text = stringResource(R.string.search_result_location_unavailable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = contentAlpha),
                        )
                    }
                }
            }
            if (isTappable) {
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

/**
 * H4: `shelf_is_system` means [result]'s shelf is the server's "Unsorted" holding shelf, which
 * the client always localizes — never the server-provided, unlocalized [SearchResultDto.shelf]
 * literal (same rule as `ShelfDto.is_system` elsewhere). The server's own pre-built
 * [SearchResultDto.path] string can't be localized by string-replacing a substring inside it,
 * so when the shelf is the system shelf the path is rebuilt client-side from `location` plus
 * [unsortedShelfLabel] instead of using `path` at all.
 *
 * Pure and Compose-free (the two localized strings are resolved by the caller via
 * `stringResource()`) so this is unit-testable without any Compose rendering.
 */
fun searchResultPathText(
    result: SearchResultDto,
    unsortedShelfLabel: String,
    locationUnavailableLabel: String,
): String =
    if (result.shelf_is_system) {
        if (result.location.isBlank()) unsortedShelfLabel else "${result.location} › $unsortedShelfLabel"
    } else {
        result.path.ifBlank {
            if (result.location.isBlank() && result.shelf.isBlank()) {
                locationUnavailableLabel
            } else {
                "${result.location} › ${result.shelf}"
            }
        }
    }
