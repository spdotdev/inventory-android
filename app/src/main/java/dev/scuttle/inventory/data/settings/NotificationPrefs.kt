package dev.scuttle.inventory.data.settings

import java.util.Calendar

private const val DEFAULT_WEEKLY_HOUR = 18
private const val DEFAULT_WEEKLY_MINUTE = 0
private const val DEFAULT_LOW_STOCK_HOUR = 18
private const val DEFAULT_LOW_STOCK_MINUTE = 0

data class NotificationPrefs(
    val appUpdatesEnabled: Boolean = true,
    // member_joined + role_changed
    val householdEventsEnabled: Boolean = true,
    val activityDigestEnabled: Boolean = false,
    val weeklySummaryEnabled: Boolean = false,
    // Calendar constant, 1..7
    val weeklyDayOfWeek: Int = Calendar.SUNDAY,
    val weeklyHour: Int = DEFAULT_WEEKLY_HOUR,
    val weeklyMinute: Int = DEFAULT_WEEKLY_MINUTE,
    val lowStockEnabled: Boolean = true,
    val lowStockHour: Int = DEFAULT_LOW_STOCK_HOUR,
    val lowStockMinute: Int = DEFAULT_LOW_STOCK_MINUTE,
)

fun NotificationPrefs.lowStockReminderSettings() = ReminderSettings(lowStockEnabled, lowStockHour, lowStockMinute)
