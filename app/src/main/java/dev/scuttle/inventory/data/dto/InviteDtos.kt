package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

// The invite endpoint returns a plain object (not a data envelope).
@Serializable
data class InviteResponse(
    val code: String,
    val link: String,
)
