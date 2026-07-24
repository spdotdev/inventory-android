package dev.scuttle.inventory

import android.content.Context
import android.content.ContextWrapper
import dev.scuttle.inventory.data.settings.ReminderSettings
import dev.scuttle.inventory.data.settings.ReminderSettingsStore
import dev.scuttle.inventory.ui.settings.ReminderViewModel
import dev.scuttle.inventory.work.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderViewModelTest {
    private class FakeReminderSettingsStore(
        initial: ReminderSettings = ReminderSettings(),
    ) : ReminderSettingsStore {
        var stored = initial

        override fun get(): ReminderSettings = stored

        override fun set(settings: ReminderSettings) {
            stored = settings
        }
    }

    // ReminderScheduler is opened (see Task 3) specifically so this test can
    // override reschedule() to record its argument instead of touching a real
    // WorkManager, which doesn't exist in a plain JVM unit test.
    private class RecordingReminderScheduler : ReminderScheduler() {
        var lastRescheduledWith: ReminderSettings? = null

        override fun reschedule(
            context: Context,
            settings: ReminderSettings,
        ) {
            lastRescheduledWith = settings
        }
    }

    // A bare ContextWrapper(null) is never actually invoked: RecordingReminderScheduler
    // overrides reschedule() to ignore its context argument entirely, so this only
    // needs to type-check as a Context, not behave like one. Avoids introducing a
    // mocking library into a codebase that otherwise hand-writes every fake.
    private fun fakeContext(): Context = ContextWrapper(null)

    @Test
    fun setEnabled_persists_and_reschedules() {
        val store = FakeReminderSettingsStore()
        val scheduler = RecordingReminderScheduler()
        val viewModel = ReminderViewModel(context = fakeContext(), store = store, scheduler = scheduler)

        viewModel.setEnabled(true)

        assertTrue(store.stored.enabled)
        assertEquals(true, scheduler.lastRescheduledWith?.enabled)
    }

    @Test
    fun setTime_persists_and_reschedules() {
        val store = FakeReminderSettingsStore()
        val scheduler = RecordingReminderScheduler()
        val viewModel = ReminderViewModel(context = fakeContext(), store = store, scheduler = scheduler)

        viewModel.setTime(hour = 18, minute = 30)

        assertEquals(18, store.stored.hour)
        assertEquals(30, store.stored.minute)
        assertEquals(18, scheduler.lastRescheduledWith?.hour)
    }
}
