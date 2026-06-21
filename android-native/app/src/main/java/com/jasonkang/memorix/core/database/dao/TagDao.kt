package com.jasonkang.memorix.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jasonkang.memorix.core.database.entity.MediaTagCrossRef
import com.jasonkang.memorix.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY label ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceMediaTags(crossRefs: List<MediaTagCrossRef>)

    @Query("DELETE FROM media_tags WHERE mediaId = :mediaId")
    suspend fun clearMediaTags(mediaId: Long)
}
