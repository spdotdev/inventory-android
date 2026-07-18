package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchResultDto(
    val id: Long,
    val name: String,
    val quantity: Int,
    val location: String,
    val shelf: String,
    val path: String,
    val household_id: Long? = null,
    val location_id: Long? = null,
    val shelf_id: Long? = null,
    // H4: default false (not nullable-no-default) so an older server that hasn't shipped this
    // field yet still decodes fine — same backward-compat DTO pattern as ShelfDto.is_system.
    // When true, [dev.scuttle.inventory.ui.search.SearchScreen] renders the localized
    // R.string.shelf_unsorted string instead of [shelf] (the server-provided, unlocalized name).
    val shelf_is_system: Boolean = false,
)

@Serializable
data class SearchListResponse(
    val data: List<SearchResultDto>,
)
