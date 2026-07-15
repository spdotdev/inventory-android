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

@Serializable
data class UpdateLocationRequest(
    val name: String,
    val type: String,
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
