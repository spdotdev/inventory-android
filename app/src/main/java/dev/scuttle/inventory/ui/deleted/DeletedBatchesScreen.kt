package dev.scuttle.inventory.ui.deleted

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.DeletedBatchDto
import dev.scuttle.inventory.ui.theme.FrostCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Recently-deleted browser: every restorable deletion batch for this household,
 * whichever surface minted it (API/Android or web) — the closer for the gap
 * where Android's own Undo snackbar was the ONLY way back once it timed out.
 * Read-only list + a single "Restore" action per row; no confirmation dialog,
 * since restoring is the inverse of a delete the user already confirmed once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedBatchesScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: DeletedBatchesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(householdId) { viewModel.load(householdId) }

    val errorText = state.errorRes?.let { stringResource(it) }
    LaunchedEffect(errorText) { errorText?.let { snackbarHostState.showSnackbar(it) } }

    val conflictText = stringResource(R.string.deleted_browser_conflict)
    LaunchedEffect(state.restoreConflict) {
        if (state.restoreConflict) {
            snackbarHostState.showSnackbar(conflictText, duration = SnackbarDuration.Short)
            viewModel.consumeRestoreConflict()
        }
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
                        stringResource(R.string.deleted_browser_title),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (!state.loading && state.batches.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.deleted_browser_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.batches, key = { it.batch }) { batch ->
                            DeletedBatchRow(batch, onRestore = { viewModel.restore(batch.batch) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedBatchRow(
    batch: DeletedBatchDto,
    onRestore: () -> Unit,
) {
    FrostCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = formatDeletedAt(batch.deleted_at), style = MaterialTheme.typography.titleSmall)
            Text(
                text =
                    stringResource(
                        R.string.deleted_browser_summary,
                        batch.locations,
                        batch.shelves,
                        batch.products,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.deleted_browser_restore_action))
                }
            }
        }
    }
}

/**
 * The server sends an ISO-8601 instant (Carbon's default JSON serialization). A
 * malformed/unparseable value falls back to the raw string rather than crashing
 * the row — this is a display nicety, not something worth losing the whole list over.
 */
internal fun formatDeletedAt(deletedAt: String): String =
    runCatching {
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(deletedAt))
    }.getOrDefault(deletedAt)
