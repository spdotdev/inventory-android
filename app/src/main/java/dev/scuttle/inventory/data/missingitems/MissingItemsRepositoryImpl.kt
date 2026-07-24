package dev.scuttle.inventory.data.missingitems

import android.util.Log
import dev.scuttle.inventory.data.api.MissingItemsApi
import javax.inject.Inject

class MissingItemsRepositoryImpl
    @Inject
    constructor(
        private val api: MissingItemsApi,
    ) : MissingItemsRepository {
        override suspend fun count(): Int? =
            try {
                api.count().data.count
            } catch (e: Exception) {
                Log.w("MissingItemsRepository", "Missing-items count check failed", e)
                null
            }
    }
