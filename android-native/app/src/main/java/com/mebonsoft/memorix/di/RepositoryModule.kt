package com.mebonsoft.memorix.di

import com.mebonsoft.memorix.core.backup.MemorixBackupManager
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
import com.mebonsoft.memorix.core.cloud.CloudSyncOperations
import com.mebonsoft.memorix.core.cloud.GoogleDriveSyncManager
import com.mebonsoft.memorix.core.monetization.AndroidProBillingRepository
import com.mebonsoft.memorix.core.monetization.DataStoreProEntitlementRepository
import com.mebonsoft.memorix.core.monetization.ProBillingRepository
import com.mebonsoft.memorix.core.monetization.ProEntitlementRepository
import com.mebonsoft.memorix.data.repository.AlbumRepository
import com.mebonsoft.memorix.data.repository.DefaultAlbumRepository
import com.mebonsoft.memorix.data.repository.DefaultMediaRepository
import com.mebonsoft.memorix.data.repository.DefaultSearchRepository
import com.mebonsoft.memorix.data.repository.MediaRepository
import com.mebonsoft.memorix.data.repository.SearchRepository
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
    abstract fun bindMediaRepository(impl: DefaultMediaRepository): MediaRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: DefaultAlbumRepository): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: DefaultSearchRepository): SearchRepository

    @Binds
    @Singleton
    abstract fun bindMemorixBackupOperations(impl: MemorixBackupManager): MemorixBackupOperations

    @Binds
    @Singleton
    abstract fun bindCloudSyncOperations(impl: GoogleDriveSyncManager): CloudSyncOperations

    @Binds
    @Singleton
    abstract fun bindProEntitlementRepository(impl: DataStoreProEntitlementRepository): ProEntitlementRepository

    @Binds
    @Singleton
    abstract fun bindProBillingRepository(impl: AndroidProBillingRepository): ProBillingRepository
}
