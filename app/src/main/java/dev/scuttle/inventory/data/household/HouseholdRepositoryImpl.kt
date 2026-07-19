package dev.scuttle.inventory.data.household

import dev.scuttle.inventory.data.api.HouseholdApi
import dev.scuttle.inventory.data.dto.CreateHouseholdRequest
import dev.scuttle.inventory.data.dto.DeleteHouseholdRequest
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.JoinHouseholdRequest
import dev.scuttle.inventory.data.dto.UpdateHouseholdRequest
import javax.inject.Inject

/** Server sends `Content-Disposition: attachment; filename="inventory-....json"`. */
private val CONTENT_DISPOSITION_FILENAME = Regex("""filename="?([^";]+)"?""")

class HouseholdRepositoryImpl
    @Inject
    constructor(
        private val api: HouseholdApi,
    ) : HouseholdRepository {
        private var cache: List<HouseholdDto>? = null

        override fun getCached(): List<HouseholdDto>? = cache

        override suspend fun list(): List<HouseholdDto> = api.list().data.also { cache = it }

        override suspend fun create(name: String): HouseholdDto = api.create(CreateHouseholdRequest(name)).data

        override suspend fun join(code: String): HouseholdDto = api.join(JoinHouseholdRequest(code)).data

        override suspend fun update(
            householdId: Long,
            name: String?,
            color: String?,
            icon: String?,
        ): HouseholdDto {
            val body = UpdateHouseholdRequest(name = name, color = color, icon = icon)
            val updated = api.update(householdId, body).data
            cache = cache?.map { if (it.id == updated.id) updated else it }
            return updated
        }

        override suspend fun leave(householdId: Long) {
            api.leave(householdId)
            cache = cache?.filter { it.id != householdId }
        }

        override suspend fun delete(
            householdId: Long,
            nameConfirmation: String,
        ) {
            api.delete(householdId, DeleteHouseholdRequest(name = nameConfirmation))
            cache = cache?.filter { it.id != householdId }
        }

        override fun clear() {
            cache = null
        }

        override suspend fun export(householdId: Long): HouseholdExportFile {
            val response = api.export(householdId)
            if (!response.isSuccessful) error("Export failed: HTTP ${response.code()}")
            val body = response.body() ?: error("Export failed: empty response body")
            // Strip any path separators before this becomes a filesystem File name —
            // defense in depth against a header that shouldn't ever carry one, since
            // this server-controlled value is about to be written to disk verbatim.
            val filename =
                response
                    .headers()["Content-Disposition"]
                    ?.let { CONTENT_DISPOSITION_FILENAME.find(it)?.groupValues?.get(1) }
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf { it.isNotBlank() }
                    ?: "inventory-household-$householdId.json"
            return HouseholdExportFile(bytes = body.bytes(), suggestedFilename = filename)
        }
    }
