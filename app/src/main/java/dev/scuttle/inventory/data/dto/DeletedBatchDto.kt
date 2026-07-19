@file:Suppress("ConstructorParameterNaming")

package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

/** One restorable deletion gesture — the Android twin of the web "Recently deleted" row. */
@Serializable
data class DeletedBatchDto(
    val batch: String,
    val deleted_at: String,
    val locations: Int,
    val shelves: Int,
    val products: Int,
    val total: Int,
)

@Serializable
data class DeletedBatchListResponse(
    val data: List<DeletedBatchDto>,
)
