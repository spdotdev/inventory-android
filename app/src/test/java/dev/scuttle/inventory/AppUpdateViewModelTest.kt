package dev.scuttle.inventory

import dev.scuttle.inventory.data.appupdate.AppUpdateRepository
import dev.scuttle.inventory.data.appupdate.UpdateStatus
import dev.scuttle.inventory.data.dto.AppReleaseDto
import dev.scuttle.inventory.ui.appupdate.AppUpdateViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val release =
        AppReleaseDto(
            id = 1,
            versionCode = 999,
            versionName = "future",
            changelog = "new stuff",
            downloadUrl = "https://example.test/app.apk",
        )

    private val newerRelease =
        release.copy(id = 2, versionCode = 1000, versionName = "even newer")

    private class FakeAppUpdateRepository(
        private val results: List<UpdateStatus>,
    ) : AppUpdateRepository {
        private var index = 0

        constructor(result: UpdateStatus) : this(listOf(result))

        override suspend fun check(): UpdateStatus {
            val result = results[minOf(index, results.size - 1)]
            index++
            return result
        }
    }

    @Test
    fun refresh_populates_status() =
        runTest {
            val viewModel = AppUpdateViewModel(FakeAppUpdateRepository(UpdateStatus.Optional(release)))

            viewModel.refresh()

            assertEquals(UpdateStatus.Optional(release), viewModel.status.value)
        }

    @Test
    fun optional_dialog_can_be_dismissed() =
        runTest {
            val viewModel = AppUpdateViewModel(FakeAppUpdateRepository(UpdateStatus.Optional(release)))
            viewModel.refresh()

            assertTrue(viewModel.isDialogVisible.value)
            viewModel.dismissOptional()
            assertFalse(viewModel.isDialogVisible.value)
        }

    @Test
    fun breaking_dialog_ignores_dismiss() =
        runTest {
            val viewModel = AppUpdateViewModel(FakeAppUpdateRepository(UpdateStatus.Breaking(release)))
            viewModel.refresh()

            viewModel.dismissOptional()

            assertTrue(viewModel.isDialogVisible.value)
        }

    @Test
    fun dismissing_one_release_does_not_suppress_a_different_newer_release() =
        runTest {
            val viewModel =
                AppUpdateViewModel(
                    FakeAppUpdateRepository(
                        listOf(UpdateStatus.Optional(release), UpdateStatus.Optional(newerRelease)),
                    ),
                )

            viewModel.refresh()
            assertTrue(viewModel.isDialogVisible.value)
            viewModel.dismissOptional()
            assertFalse(viewModel.isDialogVisible.value)

            viewModel.refresh()

            assertEquals(UpdateStatus.Optional(newerRelease), viewModel.status.value)
            assertTrue(viewModel.isDialogVisible.value)
        }
}
