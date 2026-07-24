package dev.scuttle.inventory.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.scuttle.inventory.data.appupdate.AppUpdateRepository

@HiltWorker
class AppUpdateCheckWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val repository: AppUpdateRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val status = repository.check()
            postAppUpdateNotification(applicationContext, status)
            return Result.success()
        }
    }
