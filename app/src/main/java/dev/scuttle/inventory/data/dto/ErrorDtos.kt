package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClientErrorRequest(
    val device_id: String,
    val error_code: String,
    val message: String? = null,
    val app_version: String? = null,
)
