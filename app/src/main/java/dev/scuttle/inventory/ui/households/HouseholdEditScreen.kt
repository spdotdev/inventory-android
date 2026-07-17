package dev.scuttle.inventory.ui.households

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.LiveStatusText
import dev.scuttle.inventory.ui.theme.householdAccentsByKey
import dev.scuttle.inventory.ui.theme.householdIconsByKey
import dev.scuttle.inventory.ui.theme.householdTheme

private val SWATCH_SIZE = 40.dp
private val SWATCH_ICON_SIZE = 22.dp
private const val SWATCH_BACKGROUND_ALPHA = 0.28f

/** Matches the server-side household name column limit (UpdateHouseholdRequest's `max:50`). */
private const val MAX_HOUSEHOLD_NAME_LENGTH = 50

/**
 * Rename a household, pick its colour/icon (moved out of the old palette-icon
 * dialog verbatim), and — in a visually separated danger zone — leave it.
 *
 * Reuses [HouseholdsViewModel] rather than a dedicated view model (per the
 * existing screen/ViewModel idiom, one ViewModel per feature, not per route):
 * the household list it loads is process-cached in [dev.scuttle.inventory.data.household.HouseholdRepository],
 * so arriving here from HouseholdsScreen finds the target household already
 * in [dev.scuttle.inventory.ui.households.HouseholdsUiState.households] with no extra round trip.
 * That "no extra round trip" claim only holds if the CALLER passes the SAME
 * [viewModel] instance HouseholdsScreen is using — `hiltViewModel()`'s own
 * default scopes to THIS composable's own back-stack entry, which is a
 * different [androidx.lifecycle.ViewModelStoreOwner] from HouseholdsScreen's
 * (they are two separate NavHost destinations). MainActivity's
 * `InventoryNavHost` hoists one `householdsViewModel` (same pattern as
 * `drawerViewModel`) and passes it to both screens explicitly for exactly
 * this reason — leaving the parameter at its default here would silently
 * mint a second instance, whose own `init` re-runs the cached-render/
 * refreshSilent() sequence and fires an untracked extra `GET /households`.
 *
 * The colour/icon swatches apply immediately on tap (through [HouseholdsViewModel.updateTheme]),
 * matching direct-manipulation pickers elsewhere; only the name has an explicit Save, because
 * unlike a swatch tap a name is free text that needs a confirming action.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HouseholdEditScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val household = state.households.find { it.id == householdId }

    // Wait for leave() to actually complete server-side (not just for the tap) before
    // navigating back — see the doc on HouseholdsUiState.leftHouseholdId for why
    // navigating immediately would risk cancelling the in-flight request, and for why
    // this compares against THIS screen's own householdId rather than a plain
    // boolean: this ViewModel instance is now SHARED (see the class doc above), so a
    // boolean would stay stuck true and auto-navigate back out of the very next
    // household-edit visit, for any OTHER household, before the user could do
    // anything.
    LaunchedEffect(state.leftHouseholdId) { if (state.leftHouseholdId == householdId) onBack() }

    var name by remember(household?.id) { mutableStateOf(household?.name.orEmpty()) }
    // Kept locally (rather than reading household.color/icon back from state at
    // save time) so saveName() below always has the LATEST chosen theme on hand,
    // even the instant after a swatch tap, before its own updateTheme() call has
    // round-tripped — passing a stale/absent value there would risk clearing it.
    var selectedColor by remember(household?.id) { mutableStateOf(household?.color) }
    var selectedIcon by remember(household?.id) { mutableStateOf(household?.icon) }
    var confirmLeave by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun saveName() {
        val trimmed = name.trim().take(MAX_HOUSEHOLD_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        keyboardController?.hide()
        // Pass the CURRENT theme back through: UpdateHouseholdRequest's color/icon
        // have no default and are always encoded, so an explicit null here would
        // clear the theme as a side effect of a rename.
        viewModel.update(householdId, name = trimmed, color = selectedColor, icon = selectedIcon)
    }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.household_edit_title)) },
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
        if (household != null) {
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
                state.error?.let { LiveStatusText(it) }

                // Rename + theme are restructure-scoped (HouseholdPolicy's doc comment
                // describes both as Owner/Admin actions): a Member sees the name as
                // plain text and no theme swatches at all, matching the client-side
                // gate already applied to locations/shelves edit mode, ahead of
                // HouseholdController::update() enforcing it server-side too. Leave
                // (the danger-zone card below) is NOT gated — it's a plain membership
                // action, not a restructure one.
                if (household.can_restructure) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(MAX_HOUSEHOLD_NAME_LENGTH) },
                            label = { Text(stringResource(R.string.households_field_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { saveName() }),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = ::saveName,
                            enabled = name.isNotBlank() && !state.loading,
                        ) {
                            Text(stringResource(R.string.action_save))
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            householdAccentsByKey.forEach { (key, accent) ->
                                Box(
                                    modifier =
                                        Modifier
                                            .size(SWATCH_SIZE)
                                            .clip(CircleShape)
                                            .background(accent)
                                            .selectionBorder(selected = selectedColor == key)
                                            .clickable {
                                                selectedColor = key
                                                viewModel.updateTheme(householdId, color = key, icon = selectedIcon)
                                            }.testTag("theme-color-$key"),
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.household_theme_icon_label),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val iconBackground =
                                householdTheme(householdId, selectedColor)
                                    .accent
                                    .copy(alpha = SWATCH_BACKGROUND_ALPHA)
                            householdIconsByKey.forEach { (key, image) ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .size(SWATCH_SIZE)
                                            .clip(CircleShape)
                                            .background(iconBackground)
                                            .selectionBorder(selected = selectedIcon == key)
                                            .clickable {
                                                selectedIcon = key
                                                viewModel.updateTheme(householdId, color = selectedColor, icon = key)
                                            }.testTag("theme-icon-$key"),
                                ) {
                                    Icon(
                                        imageVector = image,
                                        contentDescription = key,
                                        modifier = Modifier.size(SWATCH_ICON_SIZE),
                                    )
                                }
                            }
                        }

                        TextButton(onClick = {
                            selectedColor = null
                            selectedIcon = null
                            viewModel.updateTheme(householdId, color = null, icon = null)
                        }) {
                            Text(stringResource(R.string.household_theme_default))
                        }
                    }
                } else {
                    Text(
                        text = household.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Semantically-coloured card (error tint): stays on plain Card, not
                // FrostCard — see FrostCard's own doc comment.
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.household_edit_danger_zone_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.household_edit_danger_zone_body),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        // No "delete household" here yet — nobody owns a household today
                        // (all members are equal). That arrives with roles (Spec 2), and
                        // this danger zone is where it will land.
                        Button(
                            onClick = { confirmLeave = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.households_leave))
                        }
                    }
                }
            }
        }
    }

    if (confirmLeave && household != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.households_leave_dialog_title, household.name)) },
            text = { Text(stringResource(R.string.households_leave_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leave(householdId)
                    confirmLeave = false
                }) {
                    Text(stringResource(R.string.households_leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Modifier.selectionBorder(selected: Boolean): Modifier =
    if (selected) {
        border(width = 3.dp, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
    } else {
        this
    }
