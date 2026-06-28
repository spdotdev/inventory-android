package dev.scuttle.inventory.di

import android.content.Context
import dev.scuttle.inventory.data.settings.DefaultHouseholdStore
import dev.scuttle.inventory.data.settings.SharedPrefsDefaultHouseholdStore
import dev.scuttle.inventory.data.settings.SharedPrefsThemeModeStore
import dev.scuttle.inventory.data.settings.ThemeModeStore
import dev.scuttle.inventory.data.storage.EncryptedTokenStore
import dev.scuttle.inventory.data.storage.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore =
        EncryptedTokenStore(context)

    @Provides
    @Singleton
    fun provideThemeModeStore(@ApplicationContext context: Context): ThemeModeStore =
        SharedPrefsThemeModeStore(context)

    @Provides
    @Singleton
    fun provideDefaultHouseholdStore(@ApplicationContext context: Context): DefaultHouseholdStore =
        SharedPrefsDefaultHouseholdStore(context)
}
