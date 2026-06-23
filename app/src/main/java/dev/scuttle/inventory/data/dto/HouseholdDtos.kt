package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HouseholdDto(
    val id: Long,
    val name: String,
    val join_code: String,
)

// Laravel API Resources wrap payloads in a `data` envelope.
@Serializable
data class HouseholdListResponse(
    val data: List<HouseholdDto>,
)

@Serializable
data class HouseholdResponse(
    val data: HouseholdDto,
)

@Serializable
data class CreateHouseholdRequest(
    val name: String,
)

@Serializable
data class JoinHouseholdRequest(
    val code: String,
)
