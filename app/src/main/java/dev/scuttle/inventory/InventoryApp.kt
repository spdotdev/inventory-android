package dev.scuttle.inventory

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.scuttle.inventory.data.settings.NotificationPrefsStore
import dev.scuttle.inventory.data.settings.ReminderSettingsStore
import dev.scuttle.inventory.data.settings.lowStockReminderSettings
import dev.scuttle.inventory.work.AppUpdateCheckWorker
import dev.scuttle.inventory.work.LowStockReminderScheduler
import dev.scuttle.inventory.work.ReminderScheduler
import dev.scuttle.inventory.work.createAppUpdatesNotificationChannel
import dev.scuttle.inventory.work.createLowStockNotificationChannel
import dev.scuttle.inventory.work.createMissingItemsNotificationChannel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val APP_UPDATE_CHECK_INTERVAL_HOURS = 24L

@HiltAndroidApp
class InventoryApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderSettingsStore: ReminderSettingsStore

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var notificationPrefsStore: NotificationPrefsStore

    @Inject
    lateinit var lowStockReminderScheduler: LowStockReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createAppUpdatesNotificationChannel(this)
        createMissingItemsNotificationChannel(this)
        createLowStockNotificationChannel(this)
        scheduleAppUpdateCheck()
        reminderScheduler.ensureScheduled(this, reminderSettingsStore.get())
        lowStockReminderScheduler.ensureScheduled(this, notificationPrefsStore.get().lowStockReminderSettings())
    }

    private fun scheduleAppUpdateCheck() {
        val request =
            PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(APP_UPDATE_CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .build()
        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(
                "app_update_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
