package dev.scuttle.inventory.data.settings

/**
 * Tracks "seen" flags for one-off first-run UI hints (GAP4-L9), keyed by a caller-chosen
 * string id (e.g. [HINT_EDIT_MODE_PENCIL]). Device-scoped, same as [ThemeModeStore] /
 * [LanguageStore] — a UI hint is a fact about "has this device's user already been shown
 * this tip", not account data, so it deliberately does NOT go through
 * `data.auth.SessionCleaner`: logging out or switching accounts on the SAME device
 * shouldn't re-show a hint the person sitting at this phone has already dismissed once.
 */
interface HintsStore {
    fun hasSeen(hintId: String): Boolean

    fun markSeen(hintId: String)
}

/** Id for GAP4-L9's edit-mode pencil hint, shared by every screen that shows the pencil. */
const val HINT_EDIT_MODE_PENCIL = "edit_mode_pencil"
