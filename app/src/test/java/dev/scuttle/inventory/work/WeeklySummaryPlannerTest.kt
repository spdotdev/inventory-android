package dev.scuttle.inventory.work

import dev.scuttle.inventory.data.settings.NotificationPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

private const val WEEKLY_HOUR = 18
private const val WEEKLY_MINUTE = 0
private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS

class WeeklySummaryPlannerTest {
    private fun calendarAt(
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun prefs(enabled: Boolean = true): NotificationPrefs =
        NotificationPrefs(
            weeklySummaryEnabled = enabled,
            weeklyDayOfWeek = Calendar.SUNDAY,
            weeklyHour = WEEKLY_HOUR,
            weeklyMinute = WEEKLY_MINUTE,
        )

    @Test
    fun due_when_boundary_has_passed_since_last_post() {
        // now is Sunday 19:00, one hour after this week's boundary.
        val now = calendarAt(Calendar.SUNDAY, WEEKLY_HOUR + 1, WEEKLY_MINUTE)
        val boundary = calendarAt(Calendar.SUNDAY, WEEKLY_HOUR, WEEKLY_MINUTE).timeInMillis
        val lastPosted = boundary - ONE_DAY_MILLIS

        assertTrue(WeeklySummaryPlanner.isDue(prefs(), lastPosted, now))
    }

    @Test
    fun not_due_before_the_boundary_is_reached() {
        // now is Sunday 17:00, before this week's boundary; most recent boundary is last week.
        val now = calendarAt(Calendar.SUNDAY, WEEKLY_HOUR - 1, WEEKLY_MINUTE)
        val lastWeekBoundary = now.timeInMillis - (6 * ONE_DAY_MILLIS + ONE_HOUR_MILLIS)
        val lastPosted = lastWeekBoundary + ONE_HOUR_MILLIS

        assertFalse(WeeklySummaryPlanner.isDue(prefs(), lastPosted, now))
    }

    @Test
    fun not_due_twice_in_the_same_week() {
        // now is Sunday 19:00; lastPosted already happened after this week's boundary.
        val now = calendarAt(Calendar.SUNDAY, WEEKLY_HOUR + 1, WEEKLY_MINUTE)
        val boundary = calendarAt(Calendar.SUNDAY, WEEKLY_HOUR, WEEKLY_MINUTE).timeInMillis
        val lastPosted = boundary + ONE_HOUR_MILLIS

        assertFalse(WeeklySummaryPlanner.isDue(prefs(), lastPosted, now))
    }

    @Test
    fun disabled_is_never_due() {
        val now = calendarAt(Calendar.SUNDAY, WEEKLY_HOUR + 1, WEEKLY_MINUTE)
        val lastPosted = 0L

        assertFalse(WeeklySummaryPlanner.isDue(prefs(enabled = false), lastPosted, now))
    }
}
