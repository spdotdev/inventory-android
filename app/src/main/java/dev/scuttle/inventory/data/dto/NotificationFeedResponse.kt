package dev.scuttle.inventory.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FeedHouseholdDto(
    val id: Long,
    val name: String? = null,
)

@Serializable
data class FeedEventDto(
    val id: Long,
    val type: String,
    val household: FeedHouseholdDto? = null,
    val payload: JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NotificationFeedMeta(
    @SerialName("last_id") val lastId: Long,
)

@Serializable
data class NotificationFeedResponse(
    val data: List<FeedEventDto>,
    val meta: NotificationFeedMeta,
)
