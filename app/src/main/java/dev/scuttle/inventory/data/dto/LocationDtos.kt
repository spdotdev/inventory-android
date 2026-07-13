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
    val shelf_count: Int = 0,
    // Total products across all the location's shelves. Feeds the dialog's
    // "2 locations · 17 products" summary.
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

// No property defaults: the app's Json has encodeDefaults=false, so a defaulted
// field is OMITTED from the body — and the server 422s without deletion_batch_id.
@Serializable
data class DeleteLocationRequest(
    val strategy: String?,
    val target_location_id: Long?,
    val deletion_batch_id: String,
)
