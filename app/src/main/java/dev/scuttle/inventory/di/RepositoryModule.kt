package dev.scuttle.inventory.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.scuttle.inventory.data.appupdate.AppUpdateRepository
import dev.scuttle.inventory.data.appupdate.AppUpdateRepositoryImpl
import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.auth.AuthRepositoryImpl
import dev.scuttle.inventory.data.error.ErrorLogger
import dev.scuttle.inventory.data.error.ErrorLoggerImpl
import dev.scuttle.inventory.data.hierarchy.RestoreRepository
import dev.scuttle.inventory.data.hierarchy.RestoreRepositoryImpl
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.household.HouseholdRepositoryImpl
import dev.scuttle.inventory.data.invite.InviteRepository
import dev.scuttle.inventory.data.invite.InviteRepositoryImpl
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.location.LocationRepositoryImpl
import dev.scuttle.inventory.data.member.MemberRepository
import dev.scuttle.inventory.data.member.MemberRepositoryImpl
import dev.scuttle.inventory.data.missingitems.MissingItemsRepository
import dev.scuttle.inventory.data.missingitems.MissingItemsRepositoryImpl
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.product.ProductRepositoryImpl
import dev.scuttle.inventory.data.realtime.PusherRealtimeGateway
import dev.scuttle.inventory.data.realtime.RealtimeGateway
import dev.scuttle.inventory.data.search.SearchRepository
import dev.scuttle.inventory.data.search.SearchRepositoryImpl
import dev.scuttle.inventory.data.shelf.ShelfRepository
import dev.scuttle.inventory.data.shelf.ShelfRepositoryImpl
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
    abstract fun bindMemberRepository(impl: MemberRepositoryImpl): MemberRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindShelfRepository(impl: ShelfRepositoryImpl): ShelfRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindInviteRepository(impl: InviteRepositoryImpl): InviteRepository

    @Binds
    @Singleton
    abstract fun bindErrorLogger(impl: ErrorLoggerImpl): ErrorLogger

    @Binds
    @Singleton
    abstract fun bindRealtimeGateway(impl: PusherRealtimeGateway): RealtimeGateway

    @Binds
    @Singleton
    abstract fun bindRestoreRepository(impl: RestoreRepositoryImpl): RestoreRepository

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: AppUpdateRepositoryImpl): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindMissingItemsRepository(impl: MissingItemsRepositoryImpl): MissingItemsRepository
}
