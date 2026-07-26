package dev.scuttle.inventory.ui.settings

import android.content.Context
import android.content.ContextWrapper
import dev.scuttle.inventory.data.settings.NotificationPrefs
import dev.scuttle.inventory.data.settings.NotificationPrefsStore
import dev.scuttle.inventory.data.settings.ReminderSettings
import dev.scuttle.inventory.data.settings.lowStockReminderSettings
import dev.scuttle.inventory.work.LowStockReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowStockReminderViewModelTest {
    private class FakeNotificationPrefsStore(
        initial: NotificationPrefs = NotificationPrefs(),
    ) : NotificationPrefsStore {
        var stored = initial

        override fun get(): NotificationPrefs = stored

        override fun set(prefs: NotificationPrefs) {
            stored = prefs
        }
    }

    // LowStockReminderScheduler is opened specifically so this test can override
    // reschedule() to record its argument instead of touching a real WorkManager,
    // which doesn't exist in a plain JVM unit test.
    private class RecordingLowStockReminderScheduler : LowStockReminderScheduler() {
        var lastRescheduledWith: ReminderSettings? = null

        override fun reschedule(
            context: Context,
            settings: ReminderSettings,
        ) {
            lastRescheduledWith = settings
        }
    }

    private fun fakeContext(): Context = ContextWrapper(null)

    @Test
    fun setEnabled_persists_updates_flow_and_reschedules() {
        val store = FakeNotificationPrefsStore()
        val scheduler = RecordingLowStockReminderScheduler()
        val viewModel = LowStockReminderViewModel(context = fakeContext(), store = store, scheduler = scheduler)

        viewModel.setEnabled(false)

        assertTrue(!store.stored.lowStockEnabled)
        assertEquals(false, viewModel.settings.value.enabled)
        assertEquals(store.stored.lowStockReminderSettings(), scheduler.lastRescheduledWith)
    }

    @Test
    fun setTime_persists_updates_flow_and_reschedules() {
        val store = FakeNotificationPrefsStore()
        val scheduler = RecordingLowStockReminderScheduler()
        val viewModel = LowStockReminderViewModel(context = fakeContext(), store = store, scheduler = scheduler)

        viewModel.setTime(hour = 20, minute = 15)

        assertEquals(20, store.stored.lowStockHour)
        assertEquals(15, store.stored.lowStockMinute)
        assertEquals(20, viewModel.settings.value.hour)
        assertEquals(15, viewModel.settings.value.minute)
        assertEquals(store.stored.lowStockReminderSettings(), scheduler.lastRescheduledWith)
    }
}
