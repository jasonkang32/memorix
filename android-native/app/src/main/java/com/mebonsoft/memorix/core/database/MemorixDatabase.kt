package com.mebonsoft.memorix.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mebonsoft.memorix.core.database.dao.AlbumDao
import com.mebonsoft.memorix.core.database.dao.MediaDao
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.entity.AlbumEntity
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSearchEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaTagCrossRef
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.database.entity.TagEntity

@Database(
    entities = [
        MediaItemEntity::class,
        AlbumEntity::class,
        TagEntity::class,
        MediaTagCrossRef::class,
        MediaSearchEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class MemorixDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
    abstract fun tagDao(): TagDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS media_search")
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS media_search USING FTS4(
                        title TEXT NOT NULL,
                        note TEXT NOT NULL,
                        ocrText TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                rebuildSearchIndex(db)
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items RENAME TO media_items_old")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        space TEXT NOT NULL,
                        mediaType TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        thumbPath TEXT,
                        title TEXT NOT NULL,
                        note TEXT NOT NULL,
                        albumId INTEGER,
                        takenAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        fileSizeKb INTEGER NOT NULL,
                        durationSec INTEGER NOT NULL,
                        mimeType TEXT NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        ocrText TEXT NOT NULL,
                        isFavorite INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        isTrashed INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO media_items (
                        id, space, mediaType, filePath, thumbPath, title, note, albumId,
                        takenAt, createdAt, fileSizeKb, durationSec, mimeType, width,
                        height, ocrText, isFavorite, isArchived, isTrashed
                    )
                    SELECT
                        id, 'WORK', mediaType, filePath, thumbPath, title, note, albumId,
                        takenAt, createdAt, fileSizeKb, durationSec, mimeType, width,
                        height, ocrText, isFavorite, isArchived, isTrashed
                    FROM media_items_old
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE media_items_old")
                rebuildSearchIndex(db)
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN countryCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media_items ADD COLUMN region TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN batchGroupId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN isSecret INTEGER NOT NULL DEFAULT 0")
            }
        }

        private fun rebuildSearchIndex(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM media_search")
            db.execSQL(
                """
                INSERT INTO media_search(rowid, title, note, ocrText)
                SELECT id, title, note, ocrText FROM media_items
                """.trimIndent(),
            )
        }
    }
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
