package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LocationDto(
    val id: Long,
    val name: String,
    val type: String,
)

@Serializable
data class LocationListResponse(
    val data: List<LocationDto>,
)

@Serializable
data class LocationResponse(
    val data: LocationDto,
)

@Serializable
data class CreateLocationRequest(
    val name: String,
    val type: String,
)
