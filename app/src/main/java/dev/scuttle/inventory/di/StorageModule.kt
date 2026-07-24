package dev.scuttle.inventory.di

import android.content.ContentResolver
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.scuttle.inventory.data.settings.DefaultHouseholdStore
import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.data.settings.HintsStore
import dev.scuttle.inventory.data.settings.HouseholdViewStore
import dev.scuttle.inventory.data.settings.LanguageStore
import dev.scuttle.inventory.data.settings.ReminderSettingsStore
import dev.scuttle.inventory.data.settings.SharedPrefsDefaultHouseholdStore
import dev.scuttle.inventory.data.settings.SharedPrefsFavoritesStore
import dev.scuttle.inventory.data.settings.SharedPrefsHintsStore
import dev.scuttle.inventory.data.settings.SharedPrefsHouseholdViewStore
import dev.scuttle.inventory.data.settings.SharedPrefsLanguageStore
import dev.scuttle.inventory.data.settings.SharedPrefsReminderSettingsStore
import dev.scuttle.inventory.data.settings.SharedPrefsShelfViewStore
import dev.scuttle.inventory.data.settings.SharedPrefsThemeModeStore
import dev.scuttle.inventory.data.settings.ShelfViewStore
import dev.scuttle.inventory.data.settings.ThemeModeStore
import dev.scuttle.inventory.data.storage.EncryptedTokenStore
import dev.scuttle.inventory.data.storage.TokenStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideTokenStore(
        @ApplicationContext context: Context,
    ): TokenStore = EncryptedTokenStore(context)

    @Provides
    @Singleton
    fun provideThemeModeStore(
        @ApplicationContext context: Context,
    ): ThemeModeStore = SharedPrefsThemeModeStore(context)

    @Provides
    @Singleton
    fun provideDefaultHouseholdStore(
        @ApplicationContext context: Context,
    ): DefaultHouseholdStore = SharedPrefsDefaultHouseholdStore(context)

    @Provides
    @Singleton
    fun provideFavoritesStore(
        @ApplicationContext context: Context,
    ): FavoritesStore = SharedPrefsFavoritesStore(context)

    @Provides
    @Singleton
    fun provideLanguageStore(
        @ApplicationContext context: Context,
    ): LanguageStore = SharedPrefsLanguageStore(context)

    // GAP4-L9: device-scoped like ThemeModeStore/LanguageStore above — see HintsStore's
    // doc comment for why it's deliberately NOT wired into SessionCleaner.
    @Provides
    @Singleton
    fun provideHintsStore(
        @ApplicationContext context: Context,
    ): HintsStore = SharedPrefsHintsStore(context)

    @Provides
    @Singleton
    fun provideShelfViewStore(
        @ApplicationContext context: Context,
    ): ShelfViewStore = SharedPrefsShelfViewStore(context)

    @Provides
    @Singleton
    fun provideHouseholdViewStore(
        @ApplicationContext context: Context,
    ): HouseholdViewStore = SharedPrefsHouseholdViewStore(context)

    @Provides
    @Singleton
    fun provideReminderSettingsStore(
        @ApplicationContext context: Context,
    ): ReminderSettingsStore = SharedPrefsReminderSettingsStore(context)

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: Context,
    ): ContentResolver = context.contentResolver
}
