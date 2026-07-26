package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsNotificationPrefsStore(
    context: Context,
) : NotificationPrefsStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun get(): NotificationPrefs {
        val defaults = NotificationPrefs()
        return NotificationPrefs(
            appUpdatesEnabled = prefs.getBoolean(KEY_APP_UPDATES_ENABLED, defaults.appUpdatesEnabled),
            householdEventsEnabled = prefs.getBoolean(KEY_HOUSEHOLD_EVENTS_ENABLED, defaults.householdEventsEnabled),
            activityDigestEnabled = prefs.getBoolean(KEY_ACTIVITY_DIGEST_ENABLED, defaults.activityDigestEnabled),
            weeklySummaryEnabled = prefs.getBoolean(KEY_WEEKLY_SUMMARY_ENABLED, defaults.weeklySummaryEnabled),
            weeklyDayOfWeek = prefs.getInt(KEY_WEEKLY_DAY_OF_WEEK, defaults.weeklyDayOfWeek),
            weeklyHour = prefs.getInt(KEY_WEEKLY_HOUR, defaults.weeklyHour),
            weeklyMinute = prefs.getInt(KEY_WEEKLY_MINUTE, defaults.weeklyMinute),
            lowStockEnabled = prefs.getBoolean(KEY_LOW_STOCK_ENABLED, defaults.lowStockEnabled),
            lowStockHour = prefs.getInt(KEY_LOW_STOCK_HOUR, defaults.lowStockHour),
            lowStockMinute = prefs.getInt(KEY_LOW_STOCK_MINUTE, defaults.lowStockMinute),
        )
    }

    override fun set(prefs: NotificationPrefs) {
        this.prefs
            .edit()
            .putBoolean(KEY_APP_UPDATES_ENABLED, prefs.appUpdatesEnabled)
            .putBoolean(KEY_HOUSEHOLD_EVENTS_ENABLED, prefs.householdEventsEnabled)
            .putBoolean(KEY_ACTIVITY_DIGEST_ENABLED, prefs.activityDigestEnabled)
            .putBoolean(KEY_WEEKLY_SUMMARY_ENABLED, prefs.weeklySummaryEnabled)
            .putInt(KEY_WEEKLY_DAY_OF_WEEK, prefs.weeklyDayOfWeek)
            .putInt(KEY_WEEKLY_HOUR, prefs.weeklyHour)
            .putInt(KEY_WEEKLY_MINUTE, prefs.weeklyMinute)
            .putBoolean(KEY_LOW_STOCK_ENABLED, prefs.lowStockEnabled)
            .putInt(KEY_LOW_STOCK_HOUR, prefs.lowStockHour)
            .putInt(KEY_LOW_STOCK_MINUTE, prefs.lowStockMinute)
            .apply()
    }

    private companion object {
        const val KEY_APP_UPDATES_ENABLED = "notification_prefs_app_updates_enabled"
        const val KEY_HOUSEHOLD_EVENTS_ENABLED = "notification_prefs_household_events_enabled"
        const val KEY_ACTIVITY_DIGEST_ENABLED = "notification_prefs_activity_digest_enabled"
        const val KEY_WEEKLY_SUMMARY_ENABLED = "notification_prefs_weekly_summary_enabled"
        const val KEY_WEEKLY_DAY_OF_WEEK = "notification_prefs_weekly_day_of_week"
        const val KEY_WEEKLY_HOUR = "notification_prefs_weekly_hour"
        const val KEY_WEEKLY_MINUTE = "notification_prefs_weekly_minute"
        const val KEY_LOW_STOCK_ENABLED = "notification_prefs_low_stock_enabled"
        const val KEY_LOW_STOCK_HOUR = "notification_prefs_low_stock_hour"
        const val KEY_LOW_STOCK_MINUTE = "notification_prefs_low_stock_minute"
    }
}
