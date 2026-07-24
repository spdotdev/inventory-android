package dev.scuttle.inventory

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.scuttle.inventory.work.AppUpdateCheckWorker
import dev.scuttle.inventory.work.createAppUpdatesNotificationChannel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val APP_UPDATE_CHECK_INTERVAL_HOURS = 24L

@HiltAndroidApp
class InventoryApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createAppUpdatesNotificationChannel(this)
        scheduleAppUpdateCheck()
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
