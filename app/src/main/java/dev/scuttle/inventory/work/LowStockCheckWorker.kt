package dev.scuttle.inventory.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.scuttle.inventory.data.lowstock.LowStockRepository

@HiltWorker
class LowStockCheckWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val repository: LowStockRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val count = repository.count() ?: return Result.success()
            postLowStockNotification(applicationContext, count)
            return Result.success()
        }
    }
