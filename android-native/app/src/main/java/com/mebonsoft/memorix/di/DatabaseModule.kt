package com.mebonsoft.memorix.di

import android.content.Context
import androidx.room.Room
import com.mebonsoft.memorix.core.database.MemorixDatabase
import com.mebonsoft.memorix.core.database.dao.AlbumDao
import com.mebonsoft.memorix.core.database.dao.MediaDao
import com.mebonsoft.memorix.core.database.dao.TagDao
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
            .addMigrations(
                MemorixDatabase.MIGRATION_1_2,
                MemorixDatabase.MIGRATION_2_3,
                MemorixDatabase.MIGRATION_3_4,
                MemorixDatabase.MIGRATION_4_5,
                MemorixDatabase.MIGRATION_5_6,
                MemorixDatabase.MIGRATION_6_7,
            )
            .build()

    @Provides
    fun provideMediaDao(database: MemorixDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideAlbumDao(database: MemorixDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideTagDao(database: MemorixDatabase): TagDao = database.tagDao()
}
