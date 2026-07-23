package dev.scuttle.inventory

import dev.scuttle.inventory.data.api.AppReleaseApi
import dev.scuttle.inventory.data.appupdate.AppUpdateRepositoryImpl
import dev.scuttle.inventory.data.appupdate.UpdateStatus
import dev.scuttle.inventory.data.dto.AppReleaseDto
import dev.scuttle.inventory.data.dto.AppReleaseResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppUpdateRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAppReleaseApi(
        private val response: AppReleaseResponse? = null,
        private val throwOnCall: Boolean = false,
    ) : AppReleaseApi {
        override suspend fun latest(): AppReleaseResponse {
            if (throwOnCall) throw RuntimeException("offline")
            return response ?: AppReleaseResponse(data = null)
        }
    }

    @Test
    fun check_returns_none_when_no_release_exists() =
        runTest {
            val repository = AppUpdateRepositoryImpl(FakeAppReleaseApi())

            assertEquals(UpdateStatus.None, repository.check())
        }

    @Test
    fun check_returns_optional_for_a_newer_non_breaking_release() =
        runTest {
            val dto =
                AppReleaseDto(
                    id = 1,
                    versionCode = BuildConfig.VERSION_CODE + 1,
                    versionName = "future",
                    changelog = "new stuff",
                    downloadUrl = "https://example.test/app.apk",
                )
            val repository = AppUpdateRepositoryImpl(FakeAppReleaseApi(AppReleaseResponse(data = dto)))

            assertTrue(repository.check() is UpdateStatus.Optional)
        }

    @Test
    fun check_returns_none_when_the_api_call_fails() =
        runTest {
            val repository = AppUpdateRepositoryImpl(FakeAppReleaseApi(throwOnCall = true))

            assertEquals(UpdateStatus.None, repository.check())
        }
}
