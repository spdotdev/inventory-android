package dev.scuttle.inventory.data.settings

data class FeedState(
    val lastSeenId: Long = 0L,
    val lastWeeklySummaryAtMillis: Long = 0L,
)

interface FeedStateStore {
    fun get(): FeedState

    fun set(state: FeedState)
}
