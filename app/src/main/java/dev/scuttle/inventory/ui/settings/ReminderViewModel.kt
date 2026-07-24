package dev.scuttle.inventory.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scuttle.inventory.data.settings.ReminderSettings
import dev.scuttle.inventory.data.settings.ReminderSettingsStore
import dev.scuttle.inventory.work.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: ReminderSettingsStore,
        private val scheduler: ReminderScheduler,
    ) : ViewModel() {
        private val _settings = MutableStateFlow(store.get())
        val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

        fun setEnabled(enabled: Boolean) {
            val updated = _settings.value.copy(enabled = enabled)
            store.set(updated)
            _settings.value = updated
            scheduler.reschedule(context, updated)
        }

        fun setTime(
            hour: Int,
            minute: Int,
        ) {
            val updated = _settings.value.copy(hour = hour, minute = minute)
            store.set(updated)
            _settings.value = updated
            scheduler.reschedule(context, updated)
        }
    }
