package com.jasonkang.memorix.di

import android.content.Context
import androidx.room.Room
import com.jasonkang.memorix.core.database.MemorixDatabase
import com.jasonkang.memorix.core.database.dao.AlbumDao
import com.jasonkang.memorix.core.database.dao.MediaDao
import com.jasonkang.memorix.core.database.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MemorixDatabase =
        Room.databaseBuilder(
            context,
            MemorixDatabase::class.java,
            "memorix-native.db",
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMediaDao(database: MemorixDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideAlbumDao(database: MemorixDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideTagDao(database: MemorixDatabase): TagDao = database.tagDao()
}
