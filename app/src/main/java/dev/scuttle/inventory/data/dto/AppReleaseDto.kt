package dev.scuttle.inventory.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppReleaseDto(
    val id: Int,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("is_breaking") val isBreaking: Boolean = false,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int? = null,
    val changelog: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class AppReleaseResponse(
    val data: AppReleaseDto? = null,
)
