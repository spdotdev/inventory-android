package dev.scuttle.inventory.data.appupdate

import android.util.Log
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.data.api.AppReleaseApi
import javax.inject.Inject

class AppUpdateRepositoryImpl
    @Inject
    constructor(
        private val api: AppReleaseApi,
    ) : AppUpdateRepository {
        override suspend fun check(): UpdateStatus =
            try {
                val release = api.latest().data
                VersionComparator.classify(BuildConfig.VERSION_CODE, release)
            } catch (e: Exception) {
                Log.w("AppUpdateRepository", "Update check failed, treating as no update", e)
                UpdateStatus.None
            }
    }
