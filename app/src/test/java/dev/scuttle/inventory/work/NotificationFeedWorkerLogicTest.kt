package dev.scuttle.inventory.work

import dev.scuttle.inventory.data.dto.NotificationFeedMeta
import dev.scuttle.inventory.data.dto.NotificationFeedResponse
import dev.scuttle.inventory.data.settings.FeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun baseline_run_detected_at_fresh_cursor() {
        val state = FeedState(lastSeenId = 0L)

        assertTrue(isBaselineRun(state))
    }

    @Test
    fun after_baseline_state_advances_to_meta_last_id() {
        val state = FeedState(lastSeenId = 0L)
        val fetched = NotificationFeedResponse(data = emptyList(), meta = NotificationFeedMeta(lastId = 42L))

        val result = nextFeedState(state, fetched)

        assertEquals(42L, result?.lastSeenId)
        assertFalse(isBaselineRun(result!!))
    }

    @Test
    fun non_baseline_run_at_positive_cursor_not_treated_as_baseline() {
        val state = FeedState(lastSeenId = 5L)

        assertFalse(isBaselineRun(state))
    }
}
