package dev.scuttle.inventory

import dev.scuttle.inventory.data.api.MissingItemsApi
import dev.scuttle.inventory.data.dto.MissingItemsCountData
import dev.scuttle.inventory.data.dto.MissingItemsCountResponse
import dev.scuttle.inventory.data.missingitems.MissingItemsRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MissingItemsRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeMissingItemsApi(
        private val count: Int = 0,
        private val throwOnCall: Boolean = false,
    ) : MissingItemsApi {
        override suspend fun count(): MissingItemsCountResponse {
            if (throwOnCall) throw RuntimeException("offline")
            return MissingItemsCountResponse(data = MissingItemsCountData(count = count))
        }
    }

    @Test
    fun count_returns_the_real_count_on_success() =
        runTest {
            val repository = MissingItemsRepositoryImpl(FakeMissingItemsApi(count = 3))

            assertEquals(3, repository.count())
        }

    @Test
    fun count_returns_null_when_the_api_call_fails() =
        runTest {
            val repository = MissingItemsRepositoryImpl(FakeMissingItemsApi(throwOnCall = true))

            assertNull(repository.count())
        }
}
