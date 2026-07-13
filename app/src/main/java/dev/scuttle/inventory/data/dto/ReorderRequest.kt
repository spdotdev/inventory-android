package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

// Shared by both LocationApi.reorder and ShelfApi.reorder — locations and shelves
// are reordered the same way (a full ordered id list for the parent scope), so
// this lives here rather than duplicated in, or owned by, either DTO file.
@Serializable
data class ReorderRequest(
    val ids: List<Long>,
)
