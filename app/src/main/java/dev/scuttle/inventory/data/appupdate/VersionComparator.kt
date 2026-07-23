package dev.scuttle.inventory.data.appupdate

import dev.scuttle.inventory.data.dto.AppReleaseDto

object VersionComparator {
    fun classify(
        installedVersionCode: Int,
        release: AppReleaseDto?,
    ): UpdateStatus {
        if (release == null || release.versionCode <= installedVersionCode) {
            return UpdateStatus.None
        }

        val minSupported = release.minSupportedVersionCode
        val isHardBlocked = release.isBreaking && minSupported != null && installedVersionCode < minSupported

        return if (isHardBlocked) {
            UpdateStatus.Breaking(release)
        } else {
            UpdateStatus.Optional(release)
        }
    }
}
