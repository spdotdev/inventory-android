package dev.scuttle.inventory.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scuttle.inventory.data.settings.NotificationPrefsStore
import dev.scuttle.inventory.data.settings.ReminderSettings
import dev.scuttle.inventory.data.settings.lowStockReminderSettings
import dev.scuttle.inventory.work.LowStockReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LowStockReminderViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: NotificationPrefsStore,
        private val scheduler: LowStockReminderScheduler,
    ) : ViewModel() {
        private val _settings = MutableStateFlow(store.get().lowStockReminderSettings())
        val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

        fun setEnabled(enabled: Boolean) {
            val updatedPrefs = store.get().copy(lowStockEnabled = enabled)
            store.set(updatedPrefs)
            _settings.value = updatedPrefs.lowStockReminderSettings()
            scheduler.reschedule(context, _settings.value)
        }

        fun setTime(
            hour: Int,
            minute: Int,
        ) {
            val updatedPrefs = store.get().copy(lowStockHour = hour, lowStockMinute = minute)
            store.set(updatedPrefs)
            _settings.value = updatedPrefs.lowStockReminderSettings()
            scheduler.reschedule(context, _settings.value)
        }
    }
