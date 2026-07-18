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
    // Deliberately NO defaults on these three: a cached HouseholdDto decoded against a
    // PRE-roles backend response (missing these keys) throws MissingFieldException
    // rather than silently degrading — that's the point, not a bug to "fix" by adding
    // defaults. A default here (e.g. can_manage_members = false) would let an old
    // cached/serialized value silently decode as a real (if conservative) permission
    // flag instead of failing loudly, and this app has no local cache to protect
    // against anyway (always-online). Safe in practice: the backend already shipped
    // and deployed roles before this branch merges, so no pre-roles response exists
    // to hit this in production.
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

// Deliberately NO default on `name`: this is a typed confirmation, not a
// patch — the server 422s unless the household's EXACT current name is sent,
// and encodeDefaults=false would silently drop a defaulted property that
// happened to equal its default, producing a body the server can never accept.
@Serializable
data class DeleteHouseholdRequest(
    val name: String,
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
