package dev.scuttle.inventory.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.scuttle.inventory.data.settings.ReminderSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val MISSING_ITEMS_CHECK_WORK_NAME = "missing_items_check"
private const val REMINDER_INTERVAL_HOURS = 24L

// Open (not the Kotlin default final) so ReminderViewModelTest (Task 6) can
// subclass it with a recording fake instead of touching a real WorkManager,
// which doesn't exist in a plain JVM unit test.
open class ReminderScheduler
    @Inject
    constructor() {
        open fun reschedule(
            context: Context,
            settings: ReminderSettings,
        ) {
            val workManager = WorkManager.getInstance(context)

            if (!settings.enabled) {
                workManager.cancelUniqueWork(MISSING_ITEMS_CHECK_WORK_NAME)
                return
            }

            val delayMillis = initialDelayMillis(settings.hour, settings.minute)
            val request =
                PeriodicWorkRequestBuilder<MissingItemsCheckWorker>(REMINDER_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                MISSING_ITEMS_CHECK_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Enqueues idempotently at app boot without disturbing an already-scheduled
         * reminder's timing (unlike [reschedule]'s UPDATE policy, used when the user
         * actively changes the time).
         */
        open fun ensureScheduled(
            context: Context,
            settings: ReminderSettings,
        ) {
            if (!settings.enabled) return

            val delayMillis = initialDelayMillis(settings.hour, settings.minute)
            val request =
                PeriodicWorkRequestBuilder<MissingItemsCheckWorker>(REMINDER_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    MISSING_ITEMS_CHECK_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        internal fun initialDelayMillis(
            hour: Int,
            minute: Int,
            now: Calendar = Calendar.getInstance(),
        ): Long {
            val target = now.clone() as Calendar
            target.set(Calendar.HOUR_OF_DAY, hour)
            target.set(Calendar.MINUTE, minute)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)

            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        }
    }
