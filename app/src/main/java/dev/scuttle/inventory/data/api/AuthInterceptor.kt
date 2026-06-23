package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.storage.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Adds the stored Sanctum token as a Bearer header when present. */
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

        return chain.proceed(authorized)
    }
}
