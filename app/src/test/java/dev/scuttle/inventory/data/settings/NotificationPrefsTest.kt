package dev.scuttle.inventory.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationPrefsTest {
    @Test
    fun defaultsMatchSpec() {
        val p = NotificationPrefs()
        assertTrue(p.appUpdatesEnabled)
        assertTrue(p.householdEventsEnabled)
        assertFalse(p.activityDigestEnabled)
        assertFalse(p.weeklySummaryEnabled)
        assertEquals(Calendar.SUNDAY, p.weeklyDayOfWeek)
        assertEquals(18, p.weeklyHour)
        assertTrue(p.lowStockEnabled)
        assertEquals(18, p.lowStockHour)
        assertEquals(0, p.lowStockMinute)
    }

    @Test
    fun lowStockMapsToReminderSettings() {
        assertEquals(ReminderSettings(true, 18, 0), NotificationPrefs().lowStockReminderSettings())
    }
}
