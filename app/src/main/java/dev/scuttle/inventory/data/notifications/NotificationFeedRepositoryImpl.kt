package dev.scuttle.inventory.data.notifications

import android.util.Log
import dev.scuttle.inventory.data.api.NotificationsApi
import dev.scuttle.inventory.data.dto.NotificationFeedResponse
import javax.inject.Inject

class NotificationFeedRepositoryImpl
    @Inject
    constructor(
        private val api: NotificationsApi,
    ) : NotificationFeedRepository {
        override suspend fun fetch(after: Long): NotificationFeedResponse? =
            try {
                api.feed(after)
            } catch (e: Exception) {
                Log.w("NotificationFeedRepository", "Notification feed fetch failed", e)
                null
            }
    }
