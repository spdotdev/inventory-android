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

            // CANCEL_AND_REENQUEUE, not UPDATE: UPDATE preserves an already-scheduled
            // periodic work's existing next-run anchor and does NOT apply a new
            // request's initialDelay to it (confirmed on-device: changing the
            // reminder time a second time silently kept firing at the first-ever
            // schedule's original time). CANCEL_AND_REENQUEUE genuinely cancels and
            // reschedules, so a user-initiated time change actually takes effect.
            workManager.enqueueUniquePeriodicWork(
                MISSING_ITEMS_CHECK_WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request,
            )
        }

        /**
         * Enqueues idempotently at app boot without disturbing an already-scheduled
         * reminder's timing (unlike [reschedule]'s CANCEL_AND_REENQUEUE, used when
         * the user actively changes the time).
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
