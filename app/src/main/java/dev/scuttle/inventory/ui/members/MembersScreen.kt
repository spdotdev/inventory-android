package dev.scuttle.inventory.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.MemberDto

/**
 * Roster for one household: name + role badge per member, with promote/demote/remove
 * gated on the VIEWER's own [canManageMembers] (never in [MembersUiState] itself — the
 * caller already has the active [dev.scuttle.inventory.data.dto.HouseholdDto] and its
 * `can_manage_members`/`role`, so this screen takes them as parameters rather than
 * re-deriving them). The Owner's own row never shows promote/demote/remove — only a
 * "Transfer ownership" action, and only to the Owner themselves ([viewerRole] == "owner").
 *
 * [viewerRole]/[canManageMembers] are sourced from the caller's OWN household state
 * (`HouseholdsViewModel`, via `MainActivity`), NOT from [MembersUiState] — and that
 * state does NOT automatically refresh when [MembersViewModel.transferOwnership]
 * succeeds. The member LIST refreshes correctly (it's re-fetched from the server), but
 * `viewerRole` would otherwise stay stale at "owner" for the just-demoted viewer, whose
 * OLD row (now belonging to the new owner) would then wrongly read as "self" — offering
 * a "Transfer ownership" action guaranteed to 403. There is no client-side "my own user
 * id" to compare against instead (`UserDto.id` from login is never persisted, and the
 * members API returns no `is_self`/`user_id` marker — see the final-review fix notes),
 * so the fix here is [onOwnershipTransferred]: fired the moment a transfer succeeds, in
 * lockstep with the member-list refresh, so the caller can refresh `viewerRole` at
 * (near enough) the same time rather than leaving it stale indefinitely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    householdId: Long,
    viewerRole: String,
    canManageMembers: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOwnershipTransferred: () -> Unit = {},
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(householdId) { viewModel.load(householdId) }
    LaunchedEffect(state.error) { state.error?.let { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(state.ownershipTransferCount) {
        if (state.ownershipTransferCount > 0) onOwnershipTransferred()
    }
    val undoLabel = stringResource(R.string.delete_undo)
    LaunchedEffect(state.roleChangeEvent) {
        val event = state.roleChangeEvent ?: return@LaunchedEffect
        val message =
            if (event.newRole == "admin") {
                context.getString(R.string.members_now_admin, event.memberName)
            } else {
                context.getString(R.string.members_now_member, event.memberName)
            }
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
            )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoRoleChange(event)
        }
        viewModel.consumeRoleChangeEvent()
    }

    var confirmRemove by remember { mutableStateOf<MemberDto?>(null) }
    var showTransferPicker by remember { mutableStateOf(false) }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.members_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.members, key = { it.id }) { member ->
                    val isOwnerRow = member.role == "owner"
                    // Exactly one member can hold "owner" at a time, so when the VIEWER
                    // is the owner, the owner row IS the viewer's own row — no separate
                    // viewer-user-id needs to be threaded through just to detect that.
                    val isSelf = isOwnerRow && viewerRole == "owner"

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // "(you)" only for the Owner viewing their own row — see the
                            // isSelf comment above for why that's the ONLY case this
                            // screen can derive self-identity at all. Non-owner viewers
                            // get no suffix on any row: there's no user id to compare
                            // against (no is_self/user_id from the members API, and
                            // UserDto.id from login is never persisted), so guessing would
                            // risk labelling the WRONG member as "(you)".
                            val displayName =
                                if (isSelf) {
                                    stringResource(R.string.members_self_suffix, member.name)
                                } else {
                                    member.name
                                }
                            Text(text = displayName, modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(roleLabelRes(member.role)),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        if (isOwnerRow) {
                            if (isSelf) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { showTransferPicker = true }) {
                                        Text(stringResource(R.string.members_transfer_ownership))
                                    }
                                }
                            }
                        } else if (canManageMembers) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (member.role == "member") {
                                    TextButton(onClick = { viewModel.promote(member.id) }) {
                                        Text(stringResource(R.string.members_promote))
                                    }
                                } else {
                                    TextButton(onClick = { viewModel.demote(member.id) }) {
                                        Text(stringResource(R.string.members_demote))
                                    }
                                }
                                TextButton(onClick = { confirmRemove = member }) {
                                    Text(
                                        stringResource(R.string.members_remove),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    confirmRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.members_remove_confirm_title, member.name)) },
            text = { Text(stringResource(R.string.members_remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(member.id)
                    confirmRemove = null
                }) {
                    Text(stringResource(R.string.members_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showTransferPicker) {
        AlertDialog(
            onDismissRequest = { showTransferPicker = false },
            title = { Text(stringResource(R.string.members_transfer_ownership_pick)) },
            text = {
                LazyColumn {
                    items(state.members.filter { it.role != "owner" }, key = { it.id }) { member ->
                        TextButton(onClick = {
                            viewModel.transferOwnership(member.id)
                            showTransferPicker = false
                        }) {
                            Text(member.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTransferPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun roleLabelRes(role: String): Int =
    when (role) {
        "owner" -> R.string.role_owner
        "admin" -> R.string.role_admin
        else -> R.string.role_member
    }
