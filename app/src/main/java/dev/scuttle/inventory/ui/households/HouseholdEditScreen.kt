package dev.scuttle.inventory.ui.households

import android.content.Intent
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.theme.FrostCard
import dev.scuttle.inventory.ui.theme.householdAccentsByKey
import dev.scuttle.inventory.ui.theme.householdIconsByKey
import dev.scuttle.inventory.ui.theme.householdTheme

private val SWATCH_SIZE = 40.dp
private val SWATCH_ICON_SIZE = 22.dp
private const val SWATCH_BACKGROUND_ALPHA = 0.28f

/** Matches the server-side household name column limit (UpdateHouseholdRequest's `max:50`). */
private const val MAX_HOUSEHOLD_NAME_LENGTH = 50

/** The API suffix [BuildConfig.BASE_URL] always carries — see [webExportUrl]. */
private const val API_PATH_SUFFIX = "/api/v1/"

/**
 * GAP6-M6: household data export exists only on the web
 * (`/app/households/{id}/export`) — the app never mentions it otherwise. The API and
 * web app share one domain (see CLAUDE.md "Web Google sign-in"), so the web origin is
 * derived by stripping [BuildConfig.BASE_URL]'s `/api/v1/` suffix rather than hardcoding
 * a second host. [baseUrl] is a parameter (not read from BuildConfig directly) so this
 * is a plain, unit-testable function.
 */
internal fun webExportUrl(
    householdId: Long,
    baseUrl: String = BuildConfig.BASE_URL,
): String = baseUrl.removeSuffix(API_PATH_SUFFIX) + "/app/households/$householdId/export"

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
    onOpenMembers: () -> Unit = {},
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val household = state.households.find { it.id == householdId }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Wait for leave() to actually complete server-side (not just for the tap) before
    // navigating back — see the doc on HouseholdsUiState.leftHouseholdId for why
    // navigating immediately would risk cancelling the in-flight request, and for why
    // this compares against THIS screen's own householdId rather than a plain
    // boolean: this ViewModel instance is now SHARED (see the class doc above), so a
    // boolean would stay stuck true and auto-navigate back out of the very next
    // household-edit visit, for any OTHER household, before the user could do
    // anything.
    LaunchedEffect(state.leftHouseholdId) {
        if (state.leftHouseholdId == householdId) {
            // Close the confirm-delete dialog only once delete() has actually
            // succeeded server-side (H1) — a 422/403 never reaches here, so the
            // dialog stays open with deleteError shown inline instead.
            confirmDelete = false
            onBack()
        }
    }

    var name by remember(household?.id) { mutableStateOf(household?.name.orEmpty()) }
    // Kept locally (rather than reading household.color/icon back from state at
    // save time) so saveName() below always has the LATEST chosen theme on hand,
    // even the instant after a swatch tap, before its own updateTheme() call has
    // round-tripped — passing a stale/absent value there would risk clearing it.
    var selectedColor by remember(household?.id) { mutableStateOf(household?.color) }
    var selectedIcon by remember(household?.id) { mutableStateOf(household?.icon) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmTransferFirst by remember { mutableStateOf(false) }
    var deleteNameInput by remember(household?.id) { mutableStateOf("") }
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
                title = {
                    Text(
                        stringResource(R.string.household_edit_title),
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
                state.errorRes?.let { ErrorRetry(stringResource(it), onRetry = viewModel::refresh) }

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
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { saveName() }),
                            modifier = Modifier.weight(1f).testTag("household-name-field"),
                        )
                        Button(
                            onClick = ::saveName,
                            enabled = name.isNotBlank() && !state.loading,
                            modifier = Modifier.testTag("household-save-name"),
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
                    // Role gating (can_restructure) is otherwise invisible here — a
                    // Member just sees fewer controls than an Owner/Admin with no
                    // explanation why. Shown only in this read-only branch.
                    Text(
                        text = stringResource(R.string.household_edit_readonly_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Viewing the roster is a plain membership action, not a restructure
                // one (per the backend spec) — every member sees this row, unlike the
                // name/theme controls above which are gated on can_restructure.
                FrostCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenMembers) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("household-open-members")
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.household_edit_members_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                // GAP6-M6: capability cross-hint — export exists only on the web, and
                // this is the closest screen to where a user would look for it.
                // Deliberately NOT in the danger zone below: exporting isn't
                // destructive, and burying it next to Leave/Delete would mislabel it.
                // Plain informational text, opens the web export page in the browser.
                FrostCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, webExportUrl(householdId).toUri()),
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.household_edit_export_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
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
                        Button(
                            onClick = {
                                // A sole Owner leaving 409s server-side (a household always
                                // has exactly one Owner) — steer them to Transfer ownership
                                // first instead of opening a confirm dialog that would just
                                // fail. Every other role keeps the normal leave-confirm flow.
                                if (household.role == "owner") {
                                    confirmTransferFirst = true
                                } else {
                                    confirmLeave = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.households_leave))
                        }

                        // Delete closes the solo-owner dead end (previously a stuck
                        // 409: can't leave, and there was no delete either). Owner-only,
                        // matching HouseholdController::destroy() server-side.
                        if (household.role == "owner") {
                            Button(
                                onClick = {
                                    deleteNameInput = ""
                                    viewModel.clearDeleteError()
                                    confirmDelete = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("household-delete-button"),
                            ) {
                                Text(stringResource(R.string.household_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmLeave && household != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = {
                Text(
                    stringResource(
                        R.string.households_leave_dialog_title,
                        household.name,
                    ),
                    modifier =
                        Modifier.semantics {
                            heading()
                        },
                )
            },
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

    if (confirmTransferFirst) {
        AlertDialog(
            onDismissRequest = { confirmTransferFirst = false },
            title = {
                Text(
                    stringResource(R.string.household_edit_owner_leave_title),
                    modifier = Modifier.semantics { heading() },
                )
            },
            text = { Text(stringResource(R.string.household_edit_owner_leave_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTransferFirst = false
                    onOpenMembers()
                }) {
                    Text(stringResource(R.string.household_edit_owner_leave_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTransferFirst = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // H1: this dialog stays OPEN for the whole delete() call — closing it the
    // instant Delete is tapped (the old behaviour) meant a 422 (name race) or 403
    // (role changed under the user) lost the typed confirmation name and surfaced
    // as an unrelated top-of-screen ErrorRetry whose Retry re-fetches the
    // household LIST, not the delete. `state.loading` (set for the whole call,
    // same flag Save-name/theme swatches already gate on) disables both buttons
    // and the text field and shows a small progress indicator; `state.deleteError`
    // renders INLINE and is cleared on dismiss/retype; only a successful delete —
    // via the leftHouseholdId LaunchedEffect above — closes the dialog.
    if (confirmDelete && household != null) {
        AlertDialog(
            onDismissRequest = {
                if (!state.loading) {
                    confirmDelete = false
                    viewModel.clearDeleteError()
                }
            },
            properties = DialogProperties(dismissOnBackPress = !state.loading, dismissOnClickOutside = !state.loading),
            title = {
                Text(
                    stringResource(
                        R.string.household_delete_dialog_title,
                        household.name,
                    ),
                    modifier =
                        Modifier.semantics {
                            heading()
                        },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.household_delete_dialog_body))
                    // GAP5-M2: a non-empty, non-matching value gets an inline error
                    // (isError + supportingText) instead of a silently-disabled
                    // button — an empty field just stays plain/disabled, no error.
                    val trimmedMatches = deleteNameInput.trim() == household.name
                    val showMismatch = deleteNameInput.isNotEmpty() && !trimmedMatches
                    OutlinedTextField(
                        value = deleteNameInput,
                        onValueChange = {
                            deleteNameInput = it
                            viewModel.clearDeleteError()
                        },
                        placeholder = { Text(stringResource(R.string.household_delete_field_placeholder)) },
                        singleLine = true,
                        enabled = !state.loading,
                        isError = showMismatch,
                        supportingText = {
                            if (showMismatch) {
                                Text(stringResource(R.string.household_delete_field_mismatch))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("household-delete-confirm-field"),
                    )
                    state.deleteErrorRes?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("household-delete-error"),
                        )
                    }
                    if (state.loading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.delete(householdId, deleteNameInput.trim()) },
                    // GAP5-M2: trim the comparison — a trailing/leading space typed
                    // (or pasted) around an otherwise-correct name used to leave the
                    // button mystery-disabled forever.
                    enabled = deleteNameInput.trim() == household.name && !state.loading,
                    modifier = Modifier.testTag("household-delete-confirm-button"),
                ) {
                    Text(stringResource(R.string.household_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.clearDeleteError()
                    },
                    enabled = !state.loading,
                ) { Text(stringResource(R.string.action_cancel)) }
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
