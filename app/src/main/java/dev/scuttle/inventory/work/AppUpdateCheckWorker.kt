package dev.scuttle.inventory.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.scuttle.inventory.data.appupdate.AppUpdateRepository
import dev.scuttle.inventory.data.settings.NotificationPrefs
import dev.scuttle.inventory.data.settings.NotificationPrefsStore

internal fun shouldCheckForUpdates(prefs: NotificationPrefs): Boolean = prefs.appUpdatesEnabled

@HiltWorker
class AppUpdateCheckWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val repository: AppUpdateRepository,
        private val prefsStore: NotificationPrefsStore,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            if (!shouldCheckForUpdates(prefsStore.get())) {
                return Result.success()
            }
            val status = repository.check()
            postAppUpdateNotification(applicationContext, status)
            return Result.success()
        }
    }
