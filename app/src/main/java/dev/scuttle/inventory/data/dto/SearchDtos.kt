package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchResultDto(
    val id: Long,
    val name: String,
    val quantity: Int,
    val location: String,
    val shelf: String,
    val path: String,
)

@Serializable
data class SearchListResponse(
    val data: List<SearchResultDto>,
)
