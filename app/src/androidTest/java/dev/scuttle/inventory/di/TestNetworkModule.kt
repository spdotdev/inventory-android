package dev.scuttle.inventory.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.scuttle.inventory.MockWebServerHolder
import dev.scuttle.inventory.data.api.AuthApi
import dev.scuttle.inventory.data.api.ErrorApi
import dev.scuttle.inventory.data.api.HouseholdApi
import dev.scuttle.inventory.data.api.InviteApi
import dev.scuttle.inventory.data.api.LocationApi
import dev.scuttle.inventory.data.api.MemberApi
import dev.scuttle.inventory.data.api.ProductApi
import dev.scuttle.inventory.data.api.RestoreApi
import dev.scuttle.inventory.data.api.SearchApi
import dev.scuttle.inventory.data.api.ShelfApi
import dev.scuttle.inventory.data.storage.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * Replaces NetworkModule in instrumented tests. Retrofit is pointed at
 * MockWebServer's URL which each test sets via [MockWebServerRule].
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
object TestNetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenStore: TokenStore): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val token = tokenStore.get()
                val req =
                    if (token != null) {
                        chain
                            .request()
                            .newBuilder()
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                    } else {
                        chain.request()
                    }
                chain.proceed(req)
            }.build()

    @Provides
    @Singleton
    fun provideBaseUrl(): String = MockWebServerHolder.url

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        baseUrl: String,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun provideAuthApi(r: Retrofit): AuthApi = r.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideHouseholdApi(r: Retrofit): HouseholdApi = r.create(HouseholdApi::class.java)

    @Provides @Singleton
    fun provideMemberApi(r: Retrofit): MemberApi = r.create(MemberApi::class.java)

    @Provides @Singleton
    fun provideLocationApi(r: Retrofit): LocationApi = r.create(LocationApi::class.java)

    @Provides @Singleton
    fun provideShelfApi(r: Retrofit): ShelfApi = r.create(ShelfApi::class.java)

    @Provides @Singleton
    fun provideProductApi(r: Retrofit): ProductApi = r.create(ProductApi::class.java)

    @Provides @Singleton
    fun provideSearchApi(r: Retrofit): SearchApi = r.create(SearchApi::class.java)

    @Provides @Singleton
    fun provideInviteApi(r: Retrofit): InviteApi = r.create(InviteApi::class.java)

    @Provides @Singleton
    fun provideErrorApi(r: Retrofit): ErrorApi = r.create(ErrorApi::class.java)

    @Provides @Singleton
    fun provideRestoreApi(r: Retrofit): RestoreApi = r.create(RestoreApi::class.java)
}
