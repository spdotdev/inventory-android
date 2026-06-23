package dev.scuttle.inventory.di

import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.auth.AuthRepositoryImpl
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.household.HouseholdRepositoryImpl
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.location.LocationRepositoryImpl
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.product.ProductRepositoryImpl
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.data.shelf.ShelfRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindShelfRepository(impl: ShelfRepositoryImpl): ShelfRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository
}
