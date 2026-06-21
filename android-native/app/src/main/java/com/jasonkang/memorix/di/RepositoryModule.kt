package com.jasonkang.memorix.di

import com.jasonkang.memorix.data.repository.AlbumRepository
import com.jasonkang.memorix.data.repository.DefaultAlbumRepository
import com.jasonkang.memorix.data.repository.DefaultMediaRepository
import com.jasonkang.memorix.data.repository.DefaultSearchRepository
import com.jasonkang.memorix.data.repository.MediaRepository
import com.jasonkang.memorix.data.repository.SearchRepository
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
}
