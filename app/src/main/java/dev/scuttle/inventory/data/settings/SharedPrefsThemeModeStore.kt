package dev.scuttle.inventory.data.settings

import android.content.Context
import dev.scuttle.inventory.ui.theme.ThemeMode

class SharedPrefsThemeModeStore(context: Context) : ThemeModeStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun get(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    override fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
