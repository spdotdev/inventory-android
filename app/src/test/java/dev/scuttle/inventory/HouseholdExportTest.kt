package dev.scuttle.inventory

import dev.scuttle.inventory.data.api.HouseholdApi
import dev.scuttle.inventory.data.dto.CreateHouseholdRequest
import dev.scuttle.inventory.data.dto.DeleteHouseholdRequest
import dev.scuttle.inventory.data.dto.HouseholdListResponse
import dev.scuttle.inventory.data.dto.HouseholdResponse
import dev.scuttle.inventory.data.dto.JoinHouseholdRequest
import dev.scuttle.inventory.data.dto.UpdateHouseholdRequest
import dev.scuttle.inventory.data.household.HouseholdRepositoryImpl
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/** GAP6-M6 closed: native export (fetch bytes + the server's filename). */
class HouseholdExportTest {
    private class FakeHouseholdApi(
        private val response: Response<okhttp3.ResponseBody>,
    ) : HouseholdApi {
        override suspend fun update(
            householdId: Long,
            body: UpdateHouseholdRequest,
        ): HouseholdResponse = throw NotImplementedError()

        override suspend fun list(): HouseholdListResponse = throw NotImplementedError()

        override suspend fun create(body: CreateHouseholdRequest): HouseholdResponse = throw NotImplementedError()

        override suspend fun join(body: JoinHouseholdRequest): HouseholdResponse = throw NotImplementedError()

        override suspend fun leave(householdId: Long) = throw NotImplementedError()

        override suspend fun delete(
            householdId: Long,
            body: DeleteHouseholdRequest,
        ) = throw NotImplementedError()

        override suspend fun export(householdId: Long) = response
    }

    private fun jsonBody(text: String = "{}") = text.toResponseBody("application/json".toMediaType())

    @Test
    fun extracts_the_filename_from_content_disposition() =
        runTest {
            val headers =
                Headers.headersOf(
                    "Content-Disposition",
                    "attachment; filename=\"inventory-garage-20260719-120000.json\"",
                )
            val api = FakeHouseholdApi(Response.success(jsonBody("{\"households\":[]}"), headers))
            val repo = HouseholdRepositoryImpl(api)

            val export = repo.export(householdId = 1)

            assertEquals("inventory-garage-20260719-120000.json", export.suggestedFilename)
            assertEquals("{\"households\":[]}", String(export.bytes))
        }

    @Test
    fun falls_back_to_a_generated_name_when_the_header_is_missing() =
        runTest {
            val api = FakeHouseholdApi(Response.success(jsonBody()))
            val repo = HouseholdRepositoryImpl(api)

            val export = repo.export(householdId = 42)

            assertEquals("inventory-household-42.json", export.suggestedFilename)
        }

    @Test
    fun strips_any_path_separators_from_a_header_filename_before_it_becomes_a_file_name() =
        runTest {
            val headers = Headers.headersOf("Content-Disposition", "attachment; filename=\"../../etc/passwd\"")
            val api = FakeHouseholdApi(Response.success(jsonBody(), headers))
            val repo = HouseholdRepositoryImpl(api)

            val export = repo.export(householdId = 1)

            assertEquals("passwd", export.suggestedFilename)
        }

    @Test(expected = IllegalStateException::class)
    fun a_non_2xx_response_throws_instead_of_writing_an_error_page_to_disk() =
        runTest {
            val api = FakeHouseholdApi(Response.error(500, jsonBody("{\"message\":\"fail\"}")))
            val repo = HouseholdRepositoryImpl(api)

            repo.export(householdId = 1)
        }
}
