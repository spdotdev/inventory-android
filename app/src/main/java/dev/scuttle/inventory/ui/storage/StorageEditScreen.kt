package dev.scuttle.inventory.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.ColorSwatchPicker
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.common.IconSwatchPicker
import dev.scuttle.inventory.ui.common.storageTypeLabel

/** Matches the server-side location name column limit (same cap as StorageOverviewViewModel.onNewNameChange). */
private const val MAX_LOCATION_NAME_LENGTH = 50

/**
 * Full-page storage location editor — rename + type + colour/icon theme,
 * reached from StorageOverviewScreen's rename pencil, mirroring
 * ShelfEditScreen's shape (name field + Save, a type chip row, theme swatches
 * applying immediately on tap) minus the danger zone: delete stays on
 * StorageOverviewScreen's multi-select + strategy dialog, this screen never
 * offers it.
 *
 * Reuses [StorageOverviewViewModel] rather than a dedicated view model — same
 * idiom ShelfEditScreen already established by reusing [dev.scuttle.inventory.ui.shelves.ShelvesViewModel]
 * — so the location list StorageOverviewScreen already loaded for this
 * household is immediately on hand with no extra round trip. That "no extra
 * round trip" claim only holds if the CALLER passes the SAME [viewModel]
 * instance StorageOverviewScreen is using — MainActivity's `InventoryNavHost`
 * resolves this destination's `hiltViewModel()` against the STORAGE back-stack
 * entry explicitly, exactly like SHELF_EDIT does for SHELVES_MANAGE.
 *
 * Unlike shelves, a location has no `is_system` concept — every location
 * reaching this screen is fully editable (name, type, theme).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorageEditScreen(
    locationId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: StorageOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val location = state.locations.find { it.id == locationId }

    var name by remember(location?.id) { mutableStateOf(location?.name.orEmpty()) }
    var type by remember(location?.id) { mutableStateOf(location?.type ?: STORAGE_TYPES.first()) }
    // Kept locally (rather than reading location.color/icon back from state at
    // save time) so saveName() below always has the LATEST chosen theme on
    // hand, even the instant after a swatch tap, before its own update() call
    // has round-tripped — passing a stale/absent value there would risk
    // clearing it.
    var selectedColor by remember(location?.id) { mutableStateOf(location?.color) }
    var selectedIcon by remember(location?.id) { mutableStateOf(location?.icon) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun saveName() {
        val trimmed = name.trim().take(MAX_LOCATION_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        keyboardController?.hide()
        // Pass the CURRENT theme back through: UpdateLocationRequest's color/icon
        // have no default and are always encoded, so an explicit null here would
        // clear the theme as a side effect of a rename/type change.
        viewModel.update(locationId, name = trimmed, type = type, color = selectedColor, icon = selectedIcon)
    }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(
                        stringResource(R.string.storage_edit_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (location != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                state.errorRes?.let { ErrorRetry(stringResource(it), onRetry = viewModel::refresh) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(MAX_LOCATION_NAME_LENGTH) },
                        label = { Text(stringResource(R.string.add_storage_name_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveName() }),
                        modifier = Modifier.weight(1f).testTag("location-name-field"),
                    )
                    Button(
                        onClick = ::saveName,
                        enabled = name.isNotBlank() && !state.loading,
                        modifier = Modifier.testTag("location-save-name"),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.add_storage_type_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        STORAGE_TYPES.forEach { storageType ->
                            FilterChip(
                                selected = type == storageType,
                                onClick = { type = storageType },
                                label = { Text(storageTypeLabel(storageType)) },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.household_edit_appearance_label),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = stringResource(R.string.household_theme_color_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    ColorSwatchPicker(
                        selectedColor = selectedColor,
                        onSelect = { key ->
                            selectedColor = key
                            viewModel.updateTheme(locationId, color = key, icon = selectedIcon)
                        },
                    )

                    Text(
                        text = stringResource(R.string.household_theme_icon_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconSwatchPicker(
                        id = locationId,
                        selectedColor = selectedColor,
                        selectedIcon = selectedIcon,
                        onSelect = { key ->
                            selectedIcon = key
                            viewModel.updateTheme(locationId, color = selectedColor, icon = key)
                        },
                    )

                    TextButton(onClick = {
                        selectedColor = null
                        selectedIcon = null
                        viewModel.updateTheme(locationId, color = null, icon = null)
                    }) {
                        Text(stringResource(R.string.household_theme_default))
                    }
                }
            }
        }
    }
}
