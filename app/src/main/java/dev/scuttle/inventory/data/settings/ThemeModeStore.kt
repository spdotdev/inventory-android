package dev.scuttle.inventory.data.settings

import dev.scuttle.inventory.ui.theme.ThemeMode

/** Persists the user's theme preference. */
interface ThemeModeStore {
    fun get(): ThemeMode

    fun set(mode: ThemeMode)
}
