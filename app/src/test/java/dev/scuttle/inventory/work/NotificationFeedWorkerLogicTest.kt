package dev.scuttle.inventory.work

import dev.scuttle.inventory.data.dto.NotificationFeedMeta
import dev.scuttle.inventory.data.dto.NotificationFeedResponse
import dev.scuttle.inventory.data.settings.FeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFeedWorkerLogicTest {
    @Test
    fun null_fetch_yields_null_state() {
        val state = FeedState(lastSeenId = 5L)

        assertNull(nextFeedState(state, null))
    }

    @Test
    fun successful_fetch_advances_cursor_to_meta_last_id() {
        val state = FeedState(lastSeenId = 5L)
        val fetched = NotificationFeedResponse(data = emptyList(), meta = NotificationFeedMeta(lastId = 42L))

        val result = nextFeedState(state, fetched)

        assertEquals(42L, result?.lastSeenId)
    }

    @Test
    fun empty_page_keeps_cursor_via_echoed_after() {
        val state = FeedState(lastSeenId = 5L)
        val fetched = NotificationFeedResponse(data = emptyList(), meta = NotificationFeedMeta(lastId = 5L))

        val result = nextFeedState(state, fetched)

        assertEquals(5L, result?.lastSeenId)
    }

    @Test
    fun preserves_weekly_summary_timestamp() {
        val state = FeedState(lastSeenId = 5L, lastWeeklySummaryAtMillis = 123L)
        val fetched = NotificationFeedResponse(data = emptyList(), meta = NotificationFeedMeta(lastId = 10L))

        val result = nextFeedState(state, fetched)

        assertEquals(123L, result?.lastWeeklySummaryAtMillis)
    }
}
