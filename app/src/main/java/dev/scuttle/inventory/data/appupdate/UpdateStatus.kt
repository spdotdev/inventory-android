package dev.scuttle.inventory.data.appupdate

import dev.scuttle.inventory.data.dto.AppReleaseDto

sealed interface UpdateStatus {
    data object None : UpdateStatus

    data class Optional(
        val release: AppReleaseDto,
    ) : UpdateStatus

    data class Breaking(
        val release: AppReleaseDto,
    ) : UpdateStatus
}
