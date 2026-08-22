package com.example.nyasaplayer.core.data.di

import com.example.nyasaplayer.core.data.sync.CatalogSync
import com.example.nyasaplayer.core.data.sync.FirebaseSyncManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindCatalogSync(impl: FirebaseSyncManager): CatalogSync
}
