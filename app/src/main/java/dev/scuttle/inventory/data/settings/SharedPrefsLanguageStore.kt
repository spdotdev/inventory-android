package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsLanguageStore(
    context: Context,
) : LanguageStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun get(): AppLanguage {
        val stored = prefs.getString(KEY, null) ?: return AppLanguage.EN
        return runCatching { AppLanguage.valueOf(stored) }.getOrDefault(AppLanguage.EN)
    }

    override fun set(language: AppLanguage) {
        prefs.edit().putString(KEY, language.name).apply()
    }

    private companion object {
        const val KEY = "app_language"
    }
}
