package com.jasonkang.memorix.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSearchEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.database.entity.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isTrashed = 0 ORDER BY takenAt DESC")
    fun observeLibrary(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isTrashed = 0 ORDER BY takenAt DESC")
    suspend fun listLibrary(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE space = :space AND isTrashed = 0 ORDER BY takenAt DESC")
    fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE albumId = :albumId AND isTrashed = 0 ORDER BY takenAt DESC")
    fun observeAlbum(albumId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY takenAt DESC")
    fun observeFavorites(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isTrashed = 1 ORDER BY takenAt DESC")
    fun observeTrash(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: Long): Flow<MediaItemEntity?>

    @Query(
        """
        SELECT mi.*
        FROM media_items mi
        LEFT JOIN media_search ms ON ms.rowid = mi.id
        WHERE mi.isTrashed = 0
          AND (:space IS NULL OR mi.space = :space)
          AND (:albumId IS NULL OR mi.albumId = :albumId)
          AND (:mediaType IS NULL OR mi.mediaType = :mediaType)
          AND (
              :query IS NULL OR :query = ''
              OR ms.rowid IN (
                  SELECT rowid
                  FROM media_search
                  WHERE media_search MATCH :query
              )
          )
        ORDER BY mi.takenAt DESC
        """
    )
    fun observeSearchResults(
        query: String?,
        space: MediaSpace?,
        albumId: Long?,
        mediaType: MediaType?,
    ): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchIndex(entity: MediaSearchEntity)

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("UPDATE media_items SET isTrashed = :trashed WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean)

    @Query("UPDATE media_items SET albumId = :albumId WHERE id IN (:ids)")
    suspend fun moveToAlbum(ids: List<Long>, albumId: Long?)

    @Query("UPDATE media_items SET albumId = NULL WHERE albumId = :albumId")
    suspend fun clearAlbum(albumId: Long)

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun findById(id: Long): MediaItemEntity?
}
