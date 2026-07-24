package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsReminderSettingsStore(
    context: Context,
) : ReminderSettingsStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun get(): ReminderSettings =
        ReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR),
            minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE),
        )

    override fun set(settings: ReminderSettings) {
        prefs
            .edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_HOUR, settings.hour)
            .putInt(KEY_MINUTE, settings.minute)
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "missing_items_reminder_enabled"
        const val KEY_HOUR = "missing_items_reminder_hour"
        const val KEY_MINUTE = "missing_items_reminder_minute"
        const val DEFAULT_HOUR = 9
        const val DEFAULT_MINUTE = 0
    }
}
