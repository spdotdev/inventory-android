package dev.scuttle.inventory.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.data.api.AuthApi
import dev.scuttle.inventory.data.api.AuthInterceptor
import dev.scuttle.inventory.data.api.ErrorApi
import dev.scuttle.inventory.data.api.HouseholdApi
import dev.scuttle.inventory.data.api.InviteApi
import dev.scuttle.inventory.data.api.LocationApi
import dev.scuttle.inventory.data.api.ProductApi
import dev.scuttle.inventory.data.api.SearchApi
import dev.scuttle.inventory.data.api.ShelfApi
import dev.scuttle.inventory.data.storage.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
        }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStore: TokenStore): AuthInterceptor = AuthInterceptor(tokenStore)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideHouseholdApi(retrofit: Retrofit): HouseholdApi = retrofit.create(HouseholdApi::class.java)

    @Provides
    @Singleton
    fun provideLocationApi(retrofit: Retrofit): LocationApi = retrofit.create(LocationApi::class.java)

    @Provides
    @Singleton
    fun provideShelfApi(retrofit: Retrofit): ShelfApi = retrofit.create(ShelfApi::class.java)

    @Provides
    @Singleton
    fun provideProductApi(retrofit: Retrofit): ProductApi = retrofit.create(ProductApi::class.java)

    @Provides
    @Singleton
    fun provideSearchApi(retrofit: Retrofit): SearchApi = retrofit.create(SearchApi::class.java)

    @Provides
    @Singleton
    fun provideInviteApi(retrofit: Retrofit): InviteApi = retrofit.create(InviteApi::class.java)

    @Provides
    @Singleton
    fun provideErrorApi(retrofit: Retrofit): ErrorApi = retrofit.create(ErrorApi::class.java)
}
