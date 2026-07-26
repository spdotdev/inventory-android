package dev.scuttle.inventory.data.settings

data class FeedState(
    val lastSeenId: Long = 0L,
    val lastWeeklySummaryAtMillis: Long = 0L,
)

interface FeedStateStore {
    fun get(): FeedState

    fun set(state: FeedState)

    /** Forget the feed cursor + weekly-summary marker so one account's backlog never carries into the next session. */
    fun clear()
}
