package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsFeedStateStore(
    context: Context,
) : FeedStateStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun get(): FeedState {
        val defaults = FeedState()
        return FeedState(
            lastSeenId = prefs.getLong(KEY_LAST_SEEN_ID, defaults.lastSeenId),
            lastWeeklySummaryAtMillis = prefs.getLong(KEY_LAST_WEEKLY_SUMMARY_AT, defaults.lastWeeklySummaryAtMillis),
        )
    }

    override fun set(state: FeedState) {
        prefs
            .edit()
            .putLong(KEY_LAST_SEEN_ID, state.lastSeenId)
            .putLong(KEY_LAST_WEEKLY_SUMMARY_AT, state.lastWeeklySummaryAtMillis)
            .apply()
    }

    private companion object {
        const val KEY_LAST_SEEN_ID = "feed_state_last_seen_id"
        const val KEY_LAST_WEEKLY_SUMMARY_AT = "feed_state_last_weekly_summary_at"
    }
}
