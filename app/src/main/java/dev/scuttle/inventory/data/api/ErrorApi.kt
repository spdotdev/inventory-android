package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.ClientErrorRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ErrorApi {
    @POST("errors")
    suspend fun log(
        @Body body: ClientErrorRequest,
    )
}
