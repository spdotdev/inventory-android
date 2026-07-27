package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LocationDto(
    val id: Long,
    val name: String,
    val type: String,
    val position: Int? = null,
    // The server requires a delete STRATEGY when shelf_count > 0 — NOT when the
    // location merely holds products. Both sides read the same server relation
    // (shelvesWithContents), which counts any non-system shelf plus a system
    // "Unsorted" shelf that actually holds something. Decide `needsStrategy` from
    // THIS, or a location containing one empty shelf 422s on every delete.
    //
    // CACHE STALENESS: LocationRepositoryImpl's cache only gets replaced by
    // location-level calls (list/create/rename/reorder/delete). A shelf mutation
    // (ShelfRepository.create/delete/deleteWithStrategy) does NOT touch it, so a
    // cached LocationDto's shelf_count can read stale — e.g. still 0 right after
    // a shelf was added to a previously-empty location. Any UI deriving
    // `needsStrategy` from this field MUST go through HierarchyStore.refresh()
    // first; a cached read can silently re-create the very 422 this field exists
    // to prevent.
    val shelf_count: Int = 0,
    // Total products across all the location's shelves. Feeds the dialog's
    // "2 locations · 17 products" summary. Same cache-staleness caveat as
    // shelf_count above — refresh before trusting this after a shelf mutation.
    val product_count: Int = 0,
    // Phase-2 theme keys (same HouseholdColor/HouseholdIcon enums the household
    // and shelf themes already use server-side); null (or an older server
    // omitting them) = the client derives a stable default from the location's
    // own id (ThemedAvatar). There is no "system location" concept, so unlike
    // the shelf's Unsorted case, every location can carry a theme.
    val color: String? = null,
    val icon: String? = null,
)

@Serializable
data class LocationListResponse(
    val data: List<LocationDto>,
)

@Serializable
data class LocationResponse(
    val data: LocationDto,
)

// `color`/`icon` DEFAULT to null (unlike UpdateLocationRequest's matching
// fields) because create has no "current theme to preserve" case to protect —
// there is no existing location yet, so "the caller didn't pick a theme" and
// "the caller explicitly wants no theme" are the same thing. The server's
// create validation is `sometimes|nullable` for both, so an omitted key is
// exactly as valid as an explicit null; giving them a default just lets
// encodeDefaults=false drop the keys entirely when the add-storage sheet's
// pickers are left unselected, instead of sending `"color":null,"icon":null`
// on every create.
@Serializable
data class CreateLocationRequest(
    val name: String,
    val type: String,
    val color: String? = null,
    val icon: String? = null,
)

// Mirrors UpdateShelfRequest's deliberate asymmetry (see its own doc comment
// and CLAUDE.md's "Deletes" section — the household precedent, not a
// shelf/location-only invention): the app's Json has encodeDefaults=false and
// explicitNulls=true.
//
// - `name`/`type` DEFAULT to null on purpose. The server's rule for both is
//   `sometimes|required`/`sometimes|<enum>`: an ABSENT key passes ("don't
//   touch this field"), but an EXPLICIT null FAILS validation. A theme-only
//   update must omit both entirely, so they need the default —
//   encodeDefaults=false then drops them for us. `type` was already
//   independently updatable through this same `sometimes` rule before theming
//   existed; this just gives it the same "omit when unset" shape name always
//   had here.
// - `color` / `icon` must stay UN-defaulted. The server's rule there is
//   `sometimes|nullable`: an explicit null is meaningful (clears the theme
//   back to the derived default), so it must always be encoded, never
//   silently dropped.
@Serializable
data class UpdateLocationRequest(
    val name: String? = null,
    val type: String? = null,
    val color: String?,
    val icon: String?,
)

// `deletion_batch_id` has NO default: the app's Json has encodeDefaults=false, so a
// defaulted field is OMITTED from the body — and the server 422s without it.
//
// `strategy`/`target_location_id` DO default to null, on purpose — the mirror image
// of that same rule. The app's Json also has explicitNulls=true, so a property with
// NO default is ALWAYS encoded, even when it holds null: a strategy-less delete would
// put {"strategy":null,"target_location_id":null,...} on the wire. The server's
// `Rule::requiredIf` validates a present-but-null key as a type error (not "absent"),
// so that 422s on every delete except `move_contents` (see DeleteLocationRequest.php).
// Giving both a `= null` default means a value still equal to that default is OMITTED
// (encodeDefaults=false), matching what the server's requiredIf contract expects; a
// real strategy/target is not equal to the default, so it's still encoded as before.
@Serializable
data class DeleteLocationRequest(
    val strategy: String? = null,
    val target_location_id: Long? = null,
    val deletion_batch_id: String,
)
