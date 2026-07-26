package dev.scuttle.inventory

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.scuttle.inventory.data.settings.NotificationPrefsStore
import dev.scuttle.inventory.data.settings.ReminderSettingsStore
import dev.scuttle.inventory.data.settings.lowStockReminderSettings
import dev.scuttle.inventory.work.AppUpdateCheckWorker
import dev.scuttle.inventory.work.LowStockReminderScheduler
import dev.scuttle.inventory.work.NotificationFeedWorker
import dev.scuttle.inventory.work.ReminderScheduler
import dev.scuttle.inventory.work.createAppUpdatesNotificationChannel
import dev.scuttle.inventory.work.createFeedNotificationChannels
import dev.scuttle.inventory.work.createLowStockNotificationChannel
import dev.scuttle.inventory.work.createMissingItemsNotificationChannel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val APP_UPDATE_CHECK_INTERVAL_HOURS = 24L
private const val FEED_POLL_INTERVAL_HOURS = 6L

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
        createFeedNotificationChannels(this)
        scheduleAppUpdateCheck()
        scheduleNotificationFeedPoll()
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

    private fun scheduleNotificationFeedPoll() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request =
            PeriodicWorkRequestBuilder<NotificationFeedWorker>(FEED_POLL_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(
                "notification_feed_poll",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
