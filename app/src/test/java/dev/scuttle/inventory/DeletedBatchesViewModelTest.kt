package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.DeletedBatchDto
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.ui.deleted.DeletedBatchesViewModel
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private class FakeHouseholdRepository : HouseholdRepository {
    override fun getCached(): List<HouseholdDto>? = null

    override suspend fun list() = emptyList<HouseholdDto>()

    override suspend fun create(name: String) = throw NotImplementedError()

    override suspend fun join(code: String) = throw NotImplementedError()

    override suspend fun leave(householdId: Long) = Unit
}

private class FakeRestoreRepository : RestoreRepository {
    var batches = mutableListOf<DeletedBatchDto>()
    var restoreError: Throwable? = null
    var lastRestoredBatch: String? = null

    override suspend fun restore(
        householdId: Long,
        batchId: String,
    ): Int {
        lastRestoredBatch = batchId
        restoreError?.let { throw it }
        val match = batches.firstOrNull { it.batch == batchId }
        return match?.total ?: 0
    }

    override suspend fun listDeleted(householdId: Long): List<DeletedBatchDto> = batches
}

private fun httpException(code: Int): HttpException = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

private fun batch(id: String) =
    DeletedBatchDto(
        batch = id,
        deleted_at = "2026-07-19T00:00:00Z",
        locations = 0,
        shelves = 1,
        products = 2,
        total = 3,
    )

class DeletedBatchesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun store() = TestHierarchy.store(FakeHouseholdRepository())

    @Test
    fun loading_populates_the_batch_list() =
        runTest {
            val repo = FakeRestoreRepository().apply { batches.add(batch("b-1")) }
            val viewModel = DeletedBatchesViewModel(repo, store())

            viewModel.load(householdId = 1)

            assertEquals(
                listOf("b-1"),
                viewModel.state.value.batches
                    .map { it.batch },
            )
        }

    @Test
    fun a_successful_restore_removes_the_batch_from_the_list() =
        runTest {
            val repo = FakeRestoreRepository().apply { batches.add(batch("b-1")) }
            val viewModel = DeletedBatchesViewModel(repo, store())
            viewModel.load(householdId = 1)

            viewModel.restore("b-1")

            assertEquals(emptyList<DeletedBatchDto>(), viewModel.state.value.batches)
            assertEquals("b-1", repo.lastRestoredBatch)
        }

    @Test
    fun a_409_conflict_drops_the_batch_and_flags_the_conflict_without_a_generic_error() =
        runTest {
            val repo =
                FakeRestoreRepository().apply {
                    batches.add(batch("b-1"))
                    restoreError = httpException(409)
                }
            val viewModel = DeletedBatchesViewModel(repo, store())
            viewModel.load(householdId = 1)

            viewModel.restore("b-1")

            assertEquals(emptyList<DeletedBatchDto>(), viewModel.state.value.batches)
            assertTrue(viewModel.state.value.restoreConflict)
            assertEquals(null, viewModel.state.value.errorRes)
        }

    @Test
    fun a_network_failure_keeps_the_batch_and_surfaces_a_generic_error() =
        runTest {
            val repo =
                FakeRestoreRepository().apply {
                    batches.add(batch("b-1"))
                    restoreError = httpException(500)
                }
            val viewModel = DeletedBatchesViewModel(repo, store())
            viewModel.load(householdId = 1)

            viewModel.restore("b-1")

            assertEquals(
                listOf("b-1"),
                viewModel.state.value.batches
                    .map { it.batch },
            )
            assertTrue(viewModel.state.value.errorRes != null)
        }
}
