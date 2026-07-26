package dev.scuttle.inventory.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.settings.NotificationPrefs
import dev.scuttle.inventory.data.settings.NotificationPrefsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// No rescheduling here: the 6-hour poll worker always runs regardless of these
// prefs — they only gate what it posts, unlike the low-stock/missing-items
// reminders which each own a WorkManager schedule keyed to a user-picked time.
@HiltViewModel
class NotificationPrefsViewModel
    @Inject
    constructor(
        private val store: NotificationPrefsStore,
    ) : ViewModel() {
        private val _prefs = MutableStateFlow(store.get())
        val prefs: StateFlow<NotificationPrefs> = _prefs.asStateFlow()

        fun setHouseholdEvents(enabled: Boolean) = update { it.copy(householdEventsEnabled = enabled) }

        fun setActivityDigest(enabled: Boolean) = update { it.copy(activityDigestEnabled = enabled) }

        fun setWeeklySummaryEnabled(enabled: Boolean) = update { it.copy(weeklySummaryEnabled = enabled) }

        fun setWeeklyDay(dayOfWeek: Int) = update { it.copy(weeklyDayOfWeek = dayOfWeek) }

        fun setWeeklyTime(
            hour: Int,
            minute: Int,
        ) = update { it.copy(weeklyHour = hour, weeklyMinute = minute) }

        fun setAppUpdates(enabled: Boolean) = update { it.copy(appUpdatesEnabled = enabled) }

        private inline fun update(transform: (NotificationPrefs) -> NotificationPrefs) {
            val updated = transform(_prefs.value)
            store.set(updated)
            _prefs.value = updated
        }
    }
