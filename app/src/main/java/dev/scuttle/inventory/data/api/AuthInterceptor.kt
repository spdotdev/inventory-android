package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.storage.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Adds the stored Sanctum token as a Bearer header; clears it on 401 so next launch re-prompts login. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStore.get()

        val authorized = if (token != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        val response = chain.proceed(authorized)
        if (response.code == 401) tokenStore.clear()
        return response
    }
}
