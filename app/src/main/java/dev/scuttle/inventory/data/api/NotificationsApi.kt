package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.NotificationFeedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NotificationsApi {
    @GET("notifications")
    suspend fun feed(
        @Query("after") after: Long,
    ): NotificationFeedResponse
}
