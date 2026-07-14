package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShelfDto(
    val id: Long,
    val name: String,
    val position: Int? = null,
    val location_id: Long,
    // Server-side flag for the "Unsorted" holding shelf. The client localises the
    // label off this — the server always stores the literal name "Unsorted".
    val is_system: Boolean = false,
    // The server requires a delete STRATEGY when a shelf has products. This is how
    // the client knows to ask, and it feeds the dialog's "17 products" summary.
    //
    // CACHE STALENESS: ShelfRepositoryImpl's cache is keyed by (householdId,
    // locationId) and is only replaced by shelf-level calls for that key. A
    // product-level mutation (add/remove/move) does not update it, so a cached
    // ShelfDto's product_count can read stale. Any UI deriving `needsStrategy`
    // from this field MUST go through HierarchyStore.refresh() first; a cached
    // read can silently re-create the very 422 this field exists to prevent.
    val product_count: Int = 0,
)

@Serializable
data class ShelfListResponse(
    val data: List<ShelfDto>,
)

@Serializable
data class ShelfResponse(
    val data: ShelfDto,
)

@Serializable
data class CreateShelfRequest(
    val name: String,
)

@Serializable
data class UpdateShelfRequest(
    val name: String,
)

// `deletion_batch_id` has NO default: the app's Json has encodeDefaults=false, so a
// defaulted field is OMITTED from the body — and the server 422s without it.
//
// `strategy`/`target_shelf_id` DO default to null, on purpose — the mirror image of
// that same rule. See DeleteLocationRequest (LocationDtos.kt) for the full reasoning,
// one level up: with explicitNulls=true, a property with no default is ALWAYS encoded
// even when null, which 422s on the server's requiredIf contract for every strategy
// except move_products. A `= null` default means a value still equal to it is OMITTED.
@Serializable
data class DeleteShelfRequest(
    val strategy: String? = null,
    val target_shelf_id: Long? = null,
    val deletion_batch_id: String,
)
