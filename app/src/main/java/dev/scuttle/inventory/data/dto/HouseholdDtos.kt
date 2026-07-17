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
    val role: String,
    val can_restructure: Boolean,
    val can_manage_members: Boolean,
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

// The app's Json has encodeDefaults=false: a property equal to its default is
// OMITTED from the body. That's normally the wrong default for a request DTO —
// but `name` here is the deliberate exception, and the asymmetry matters:
//
// - `name` DEFAULTS to null on purpose. Laravel's rule is `sometimes|required`:
//   an ABSENT key passes ("don't touch the name"), but an EXPLICIT null FAILS
//   validation. A theme-only update must omit `name` entirely, so it needs the
//   default — encodeDefaults=false then drops it for us. Do not remove this
//   default; doing so makes every color/icon-only update send `"name":null`
//   and 422.
// - `color` / `icon` must stay UN-defaulted. Laravel's rule there is
//   `sometimes|nullable`: an explicit null is meaningful (clears the theme back
//   to the derived default), so it must always be encoded, never silently
//   dropped.
@Serializable
data class UpdateHouseholdRequest(
    val name: String? = null,
    val color: String?,
    val icon: String?,
)
