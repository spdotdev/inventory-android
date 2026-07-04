package dev.scuttle.inventory.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Surfaces a one-shot action error (a failed optimistic mutation — save, delete,
 * quantity change, upload) as a transient [androidx.compose.material3.Snackbar],
 * then consumes it via [onConsumed] so it doesn't re-show on recomposition or
 * re-announce to TalkBack. Material3's Snackbar is itself a live region, so the
 * message is announced without extra semantics.
 *
 * Pair with a `SnackbarHost(snackbarHostState)` in the screen's Scaffold. Screens
 * whose primary error is a *load* failure keep the inline [ErrorRetry] instead —
 * it offers a retry affordance a transient snackbar can't.
 */
@Composable
fun SnackbarErrorEffect(
    error: String?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            onConsumed()
        }
    }
}
