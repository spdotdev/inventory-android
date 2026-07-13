package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HouseholdDto(
    val id: Long,
    val name: String,
    val join_code: String,
    // Phase-2 theme keys; null (or an older server omitting them) = the client
    // derives a stable default from the id (HouseholdTheme.kt).
    val color: String? = null,
    val icon: String? = null,
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

// No property defaults on purpose: the app's Json has encodeDefaults=false, so a
// defaulted field would be OMITTED from the body and the server would keep the
// old value instead of clearing it. Explicit null = clear back to the derived
// default. `name` is nullable so a theme-only change doesn't have to resend it.
@Serializable
data class UpdateHouseholdRequest(
    val name: String?,
    val color: String?,
    val icon: String?,
)
