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

@Serializable
data class ReorderRequest(
    val ids: List<Long>,
)

// No property defaults: the app's Json has encodeDefaults=false, so a defaulted
// field is OMITTED from the body — and the server 422s without deletion_batch_id.
@Serializable
data class DeleteShelfRequest(
    val strategy: String?,
    val target_shelf_id: Long?,
    val deletion_batch_id: String,
)
