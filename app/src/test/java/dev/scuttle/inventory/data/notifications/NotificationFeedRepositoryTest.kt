package dev.scuttle.inventory.data.notifications

import dev.scuttle.inventory.data.api.NotificationsApi
import dev.scuttle.inventory.data.dto.FeedEventDto
import dev.scuttle.inventory.data.dto.FeedHouseholdDto
import dev.scuttle.inventory.data.dto.NotificationFeedMeta
import dev.scuttle.inventory.data.dto.NotificationFeedResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFeedRepositoryTest {
    private class FakeNotificationsApi(
        private val response: NotificationFeedResponse? = null,
        private val throwOnCall: Boolean = false,
    ) : NotificationsApi {
        override suspend fun feed(after: Long): NotificationFeedResponse {
            if (throwOnCall) throw RuntimeException("offline")
            return response!!
        }
    }

    @Test
    fun fetch_returns_the_response_on_success() =
        runTest {
            val response =
                NotificationFeedResponse(
                    data =
                        listOf(
                            FeedEventDto(
                                id = 7,
                                type = "member_joined",
                                household = FeedHouseholdDto(id = 3, name = "Home"),
                                payload = null,
                                createdAt = "2026-07-26T00:00:00Z",
                            ),
                        ),
                    meta = NotificationFeedMeta(lastId = 7),
                )
            val repository = NotificationFeedRepositoryImpl(FakeNotificationsApi(response = response))

            val result = repository.fetch(after = 0)

            assertEquals(response, result)
            assertEquals(7L, result?.meta?.lastId)
        }

    @Test
    fun fetch_returns_null_when_the_api_call_fails() =
        runTest {
            val repository = NotificationFeedRepositoryImpl(FakeNotificationsApi(throwOnCall = true))

            assertNull(repository.fetch(after = 0))
        }
}
