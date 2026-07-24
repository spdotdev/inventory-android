package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsReminderSettingsStore(
    context: Context,
) : ReminderSettingsStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun get(): ReminderSettings =
        ReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, 9),
            minute = prefs.getInt(KEY_MINUTE, 0),
        )

    override fun set(settings: ReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_HOUR, settings.hour)
            .putInt(KEY_MINUTE, settings.minute)
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "missing_items_reminder_enabled"
        const val KEY_HOUR = "missing_items_reminder_hour"
        const val KEY_MINUTE = "missing_items_reminder_minute"
    }
}
