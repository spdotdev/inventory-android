package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.AppReleaseResponse
import retrofit2.http.GET

interface AppReleaseApi {
    @GET("app-version")
    suspend fun latest(): AppReleaseResponse
}
