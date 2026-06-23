package dev.scuttle.inventory.di

import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.auth.AuthRepositoryImpl
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.household.HouseholdRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHouseholdRepository(impl: HouseholdRepositoryImpl): HouseholdRepository
}
