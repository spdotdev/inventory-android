package dev.scuttle.inventory.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.scuttle.inventory.data.dto.NotificationFeedResponse
import dev.scuttle.inventory.data.lowstock.LowStockRepository
import dev.scuttle.inventory.data.missingitems.MissingItemsRepository
import dev.scuttle.inventory.data.notifications.NotificationFeedRepository
import dev.scuttle.inventory.data.settings.FeedState
import dev.scuttle.inventory.data.settings.FeedStateStore
import dev.scuttle.inventory.data.settings.NotificationPrefsStore
import java.util.Calendar

/**
 * Pure cursor-advance rule for a feed poll: no fetch (network failure) means
 * retry without moving the cursor; a successful fetch (even an empty page)
 * advances the cursor to whatever `after` the server echoes back in `meta`.
 */
internal fun nextFeedState(
    state: FeedState,
    fetched: NotificationFeedResponse?,
): FeedState? {
    if (fetched == null) return null
    return state.copy(lastSeenId = fetched.meta.lastId)
}

/**
 * A fresh or just-cleared cursor (lastSeenId=0, e.g. first install or post-logout)
 * has no real "since" point — treating its first fetch as a normal poll would post
 * up to a full page of pre-existing notifications as if they just happened. Such a
 * run persists the cursor but posts nothing, and also skips the weekly-summary
 * check for that run since a fresh state's lastWeeklySummaryAtMillis is 0 too.
 */
internal fun isBaselineRun(state: FeedState): Boolean = state.lastSeenId == 0L

// One constructor parameter per collaborator this worker needs to fan out to
// (feed page + prefs/cursor state + the two summary counters); a pure fan-out,
// not a design smell to split up.
@Suppress("LongParameterList")
@HiltWorker
class NotificationFeedWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val feedRepository: NotificationFeedRepository,
        private val prefsStore: NotificationPrefsStore,
        private val feedStateStore: FeedStateStore,
        private val missingItemsRepository: MissingItemsRepository,
        private val lowStockRepository: LowStockRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val prefs = prefsStore.get()
            val state = feedStateStore.get()
            val baseline = isBaselineRun(state)
            val page = feedRepository.fetch(after = state.lastSeenId)
            val advanced = nextFeedState(state, page) ?: return Result.retry()

            checkNotNull(page)
            if (!baseline) {
                FeedDigester.digest(page.data, prefs).forEach { postPlanned(applicationContext, it) }
            }
            feedStateStore.set(advanced)

            val weeklySummaryDue =
                WeeklySummaryPlanner.isDue(prefs, advanced.lastWeeklySummaryAtMillis, Calendar.getInstance())
            if (!baseline && weeklySummaryDue) {
                val missing = missingItemsRepository.count()
                val low = lowStockRepository.count()
                if (missing != null && low != null) {
                    postWeeklySummary(applicationContext, missing, low)
                    val stamped = feedStateStore.get().copy(lastWeeklySummaryAtMillis = System.currentTimeMillis())
                    feedStateStore.set(stamped)
                }
            }
            return Result.success()
        }
    }
