package com.jasonkang.memorix.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jasonkang.memorix.core.database.entity.AlbumEntity
import com.jasonkang.memorix.core.database.entity.AlbumSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query(
        """
        SELECT 
            a.id,
            a.title,
            a.memo,
            a.dateStart,
            a.dateEnd,
            a.coverMediaId,
            a.createdAt,
            COUNT(m.id) AS itemCount,
            COALESCE(cm.thumbPath, cm.filePath) AS coverPath
        FROM albums a
        LEFT JOIN media_items m ON m.albumId = a.id AND m.isTrashed = 0
        LEFT JOIN media_items cm ON cm.id = a.coverMediaId
        GROUP BY a.id
        ORDER BY a.createdAt DESC
        """
    )
    fun observeAlbumSummaries(): Flow<List<AlbumSummary>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbum(id: Long): Flow<AlbumEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun delete(id: Long)
}
