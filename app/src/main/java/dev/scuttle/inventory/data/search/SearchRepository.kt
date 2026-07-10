package dev.scuttle.inventory.data.search

import dev.scuttle.inventory.data.dto.SearchResultDto

interface SearchRepository {
    suspend fun search(
        householdId: Long,
        query: String,
    ): List<SearchResultDto>
}
