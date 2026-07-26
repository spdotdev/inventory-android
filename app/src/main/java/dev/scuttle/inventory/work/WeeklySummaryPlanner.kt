package dev.scuttle.inventory.work

import dev.scuttle.inventory.data.settings.NotificationPrefs
import java.util.Calendar

private const val DAYS_PER_WEEK = 7

/**
 * Pure due-time computation for the weekly summary notification. No Android
 * imports so it's directly unit-testable on the JVM; the caller supplies
 * `now` for testability.
 */
object WeeklySummaryPlanner {
    fun isDue(
        prefs: NotificationPrefs,
        lastPostedMillis: Long,
        now: Calendar,
    ): Boolean {
        if (!prefs.weeklySummaryEnabled) return false

        val boundary = now.clone() as Calendar
        boundary.set(Calendar.DAY_OF_WEEK, prefs.weeklyDayOfWeek)
        boundary.set(Calendar.HOUR_OF_DAY, prefs.weeklyHour)
        boundary.set(Calendar.MINUTE, prefs.weeklyMinute)
        boundary.set(Calendar.SECOND, 0)
        boundary.set(Calendar.MILLISECOND, 0)

        if (boundary.timeInMillis > now.timeInMillis) {
            boundary.add(Calendar.DAY_OF_YEAR, -DAYS_PER_WEEK)
        }

        return boundary.timeInMillis > lastPostedMillis
    }
}
