package dev.scuttle.inventory.data.notifications

import dev.scuttle.inventory.data.dto.NotificationFeedResponse

interface NotificationFeedRepository {
    suspend fun fetch(after: Long): NotificationFeedResponse?
}
