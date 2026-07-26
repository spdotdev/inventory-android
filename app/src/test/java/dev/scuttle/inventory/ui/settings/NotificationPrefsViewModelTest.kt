package dev.scuttle.inventory.ui.settings

import dev.scuttle.inventory.data.settings.NotificationPrefs
import dev.scuttle.inventory.data.settings.NotificationPrefsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationPrefsViewModelTest {
    private class FakeNotificationPrefsStore(
        initial: NotificationPrefs = NotificationPrefs(),
    ) : NotificationPrefsStore {
        var stored = initial

        override fun get(): NotificationPrefs = stored

        override fun set(prefs: NotificationPrefs) {
            stored = prefs
        }
    }

    @Test
    fun setHouseholdEvents_persists_and_updates_flow() {
        val store = FakeNotificationPrefsStore()
        val viewModel = NotificationPrefsViewModel(store)

        viewModel.setHouseholdEvents(false)

        assertFalse(store.stored.householdEventsEnabled)
        assertFalse(viewModel.prefs.value.householdEventsEnabled)
    }

    @Test
    fun setActivityDigest_persists_and_updates_flow() {
        val store = FakeNotificationPrefsStore()
        val viewModel = NotificationPrefsViewModel(store)

        viewModel.setActivityDigest(true)

        assertTrue(store.stored.activityDigestEnabled)
        assertTrue(viewModel.prefs.value.activityDigestEnabled)
    }

    @Test
    fun setWeeklySummaryEnabled_persists_and_updates_flow() {
        val store = FakeNotificationPrefsStore()
        val viewModel = NotificationPrefsViewModel(store)

        viewModel.setWeeklySummaryEnabled(true)

        assertTrue(store.stored.weeklySummaryEnabled)
        assertTrue(viewModel.prefs.value.weeklySummaryEnabled)
    }

    @Test
    fun setWeeklyDay_persists_and_updates_flow() {
        val store = FakeNotificationPrefsStore()
        val viewModel = NotificationPrefsViewModel(store)

        viewModel.setWeeklyDay(Calendar.MONDAY)

        assertEquals(Calendar.MONDAY, store.stored.weeklyDayOfWeek)
        assertEquals(Calendar.MONDAY, viewModel.prefs.value.weeklyDayOfWeek)
    }

    @Test
    fun setWeeklyTime_persists_and_updates_flow() {
        val store = FakeNotificationPrefsStore()
        val viewModel = NotificationPrefsViewModel(store)

        viewModel.setWeeklyTime(hour = 19, minute = 45)

        assertEquals(19, store.stored.weeklyHour)
        assertEquals(45, store.stored.weeklyMinute)
        assertEquals(19, viewModel.prefs.value.weeklyHour)
        assertEquals(45, viewModel.prefs.value.weeklyMinute)
    }

    @Test
    fun setAppUpdates_persists_and_updates_flow() {
        val store = FakeNotificationPrefsStore()
        val viewModel = NotificationPrefsViewModel(store)

        viewModel.setAppUpdates(false)

        assertFalse(store.stored.appUpdatesEnabled)
        assertFalse(viewModel.prefs.value.appUpdatesEnabled)
    }
}
