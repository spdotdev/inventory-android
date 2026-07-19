package dev.scuttle.inventory.data.hierarchy

import dev.scuttle.inventory.data.api.RestoreApi
import dev.scuttle.inventory.data.dto.DeletedBatchDto
import javax.inject.Inject

class RestoreRepositoryImpl
    @Inject
    constructor(
        private val api: RestoreApi,
    ) : RestoreRepository {
        override suspend fun restore(
            householdId: Long,
            batchId: String,
        ): Int = api.restore(householdId, batchId).restored

        override suspend fun listDeleted(householdId: Long): List<DeletedBatchDto> = api.listDeleted(householdId).data
    }
