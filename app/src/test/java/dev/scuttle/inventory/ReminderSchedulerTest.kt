package dev.scuttle.inventory

import dev.scuttle.inventory.work.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderSchedulerTest {
    private val scheduler = ReminderScheduler()

    private fun calendarAt(
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun schedules_later_today_when_the_target_time_has_not_passed_yet() {
        val now = calendarAt(hour = 8, minute = 0)

        val delay = scheduler.initialDelayMillis(hour = 9, minute = 0, now = now)

        assertEquals(60 * 60 * 1000L, delay)
    }

    @Test
    fun schedules_tomorrow_when_the_target_time_has_already_passed_today() {
        val now = calendarAt(hour = 10, minute = 0)

        val delay = scheduler.initialDelayMillis(hour = 9, minute = 0, now = now)

        assertEquals(23 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun schedules_tomorrow_when_the_target_time_is_exactly_now() {
        val now = calendarAt(hour = 9, minute = 0)

        val delay = scheduler.initialDelayMillis(hour = 9, minute = 0, now = now)

        assertEquals(24 * 60 * 60 * 1000L, delay)
    }
}
