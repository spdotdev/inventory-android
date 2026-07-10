package dev.scuttle.inventory.data.search

import dev.scuttle.inventory.data.api.SearchApi
import dev.scuttle.inventory.data.dto.SearchResultDto
import javax.inject.Inject

class SearchRepositoryImpl
    @Inject
    constructor(
        private val api: SearchApi,
    ) : SearchRepository {
        override suspend fun search(
            householdId: Long,
            query: String,
        ): List<SearchResultDto> = api.search(householdId, query).data
    }
