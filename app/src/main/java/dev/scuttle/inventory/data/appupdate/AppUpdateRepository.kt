package dev.scuttle.inventory.data.appupdate

interface AppUpdateRepository {
    suspend fun check(): UpdateStatus
}
