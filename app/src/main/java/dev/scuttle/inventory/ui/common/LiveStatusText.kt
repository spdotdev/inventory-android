package dev.scuttle.inventory.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * An inline status line (error or success) that TalkBack announces the moment it
 * appears, via `liveRegion = Assertive` — the accessibility pattern established on
 * AuthScreen and [ErrorRetry]. Use it for the screens whose status text has no
 * Retry affordance (search/join/action feedback), so the announcement isn't left
 * silent until the text happens to be focused (W8).
 */
@Composable
fun LiveStatusText(
    message: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.error,
) {
    Text(
        text = message,
        color = color,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}
