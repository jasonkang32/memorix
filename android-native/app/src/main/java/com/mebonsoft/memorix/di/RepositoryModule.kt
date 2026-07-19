package com.mebonsoft.memorix.di

import com.mebonsoft.memorix.core.backup.MemorixBackupManager
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
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
}
