package dev.scuttle.inventory.ui.hierarchy

/**
 * One-shot result of an undoDelete() call, consumed by the screen to show the
 * matching localized snackbar (R.string.delete_undone / R.string.delete_undo_failed)
 * — final review, ALSO FIX: both strings existed with zero usages, so a 409 from the
 * restore endpoint (the batch was already restored elsewhere, or permanently removed
 * past the undo window) silently fell through to a generic "Something went wrong."
 * instead of the spec's message, and a successful undo had no confirmation at all.
 *
 * Deliberately NOT the pre-baked message string itself: undoDelete() runs in a
 * ViewModel, which has no locale-correct way to resolve a string resource (this
 * app's language switch is a per-Activity Configuration override — see
 * MainActivity.attachBaseContext — invisible to the singleton Application context a
 * ViewModel could otherwise inject). The screen's own Composable, which already has
 * a correctly-localized [androidx.compose.ui.res.stringResource], turns this into text.
 */
enum class UndoOutcome {
    SUCCESS,
    FAILURE,
}
