package dev.scuttle.inventory

import dev.scuttle.inventory.data.appupdate.UpdateStatus
import dev.scuttle.inventory.data.appupdate.VersionComparator
import dev.scuttle.inventory.data.dto.AppReleaseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    private fun release(
        versionCode: Int,
        isBreaking: Boolean = false,
        minSupportedVersionCode: Int? = null,
    ) = AppReleaseDto(
        id = 1,
        versionCode = versionCode,
        versionName = "0.1.$versionCode",
        isBreaking = isBreaking,
        minSupportedVersionCode = minSupportedVersionCode,
        changelog = "test",
        downloadUrl = "https://example.test/app.apk",
    )

    @Test
    fun no_release_is_none() {
        assertEquals(UpdateStatus.None, VersionComparator.classify(21, null))
    }

    @Test
    fun release_at_or_below_installed_is_none() {
        assertEquals(UpdateStatus.None, VersionComparator.classify(21, release(versionCode = 21)))
        assertEquals(UpdateStatus.None, VersionComparator.classify(21, release(versionCode = 20)))
    }

    @Test
    fun newer_non_breaking_release_is_optional() {
        val result = VersionComparator.classify(21, release(versionCode = 22, isBreaking = false))
        assertTrue(result is UpdateStatus.Optional)
    }

    @Test
    fun newer_breaking_release_with_installed_equal_to_min_is_optional_not_breaking() {
        val result =
            VersionComparator.classify(
                installedVersionCode = 20,
                release = release(versionCode = 22, isBreaking = true, minSupportedVersionCode = 20),
            )
        assertTrue(result is UpdateStatus.Optional)
    }

    @Test
    fun newer_breaking_release_with_installed_below_min_is_breaking() {
        val result =
            VersionComparator.classify(
                installedVersionCode = 19,
                release = release(versionCode = 22, isBreaking = true, minSupportedVersionCode = 20),
            )
        assertTrue(result is UpdateStatus.Breaking)
    }
}
