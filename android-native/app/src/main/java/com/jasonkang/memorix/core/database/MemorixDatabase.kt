package com.jasonkang.memorix.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.jasonkang.memorix.core.database.dao.AlbumDao
import com.jasonkang.memorix.core.database.dao.MediaDao
import com.jasonkang.memorix.core.database.dao.TagDao
import com.jasonkang.memorix.core.database.entity.AlbumEntity
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSearchEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.database.entity.MediaTagCrossRef
import com.jasonkang.memorix.core.database.entity.MediaType
import com.jasonkang.memorix.core.database.entity.TagEntity

@Database(
    entities = [
        MediaItemEntity::class,
        AlbumEntity::class,
        TagEntity::class,
        MediaTagCrossRef::class,
        MediaSearchEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class MemorixDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
    abstract fun tagDao(): TagDao
}

class DatabaseConverters {
    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun mediaSpaceToString(value: MediaSpace): String = value.name

    @TypeConverter
    fun stringToMediaSpace(value: String): MediaSpace = MediaSpace.valueOf(value)
}
