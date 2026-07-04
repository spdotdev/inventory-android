package dev.scuttle.inventory.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R

/**
 * A read-screen error line with an explicit Retry action (in addition to the
 * pull-to-refresh the list screens already offer). Pair with [toUserMessage]
 * so the message is friendly (e.g. "Can't reach the server…") rather than raw.
 */
@Composable
fun ErrorRetry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
