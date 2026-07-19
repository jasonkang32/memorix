package com.mebonsoft.memorix.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mebonsoft.memorix.core.database.entity.MediaTagCrossRef
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagUsageSummary(
    val id: Long,
    val key: String,
    val label: String,
    val colorHex: String,
    val iconName: String,
    val usageCount: Int,
)

data class MediaTagAssignment(
    val mediaId: Long,
    val tagId: Long,
    val label: String,
)

data class ManagedTagSummary(
    val id: Long,
    val label: String,
    val usageCount: Int,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY label ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT
            tags.id AS id,
            tags.label AS label,
            COUNT(DISTINCT CASE
                WHEN media_items.batchGroupId != '' THEN media_items.batchGroupId
                ELSE 'legacy-' || media_items.id
            END) AS usageCount
        FROM tags
        LEFT JOIN media_tags ON tags.id = media_tags.tagId
        LEFT JOIN media_items ON media_items.id = media_tags.mediaId
        GROUP BY tags.id, tags.label
        ORDER BY tags.label ASC
        """,
    )
    fun observeManagedTags(): Flow<List<ManagedTagSummary>>

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN media_tags ON tags.id = media_tags.tagId
        WHERE media_tags.mediaId = :mediaId
        ORDER BY tags.label ASC
        """,
    )
    fun observeTagsForMedia(mediaId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT media_tags.mediaId AS mediaId, tags.id AS tagId, tags.label AS label
        FROM media_tags
        INNER JOIN tags ON tags.id = media_tags.tagId
        ORDER BY tags.label ASC
        """,
    )
    fun observeMediaTagAssignments(): Flow<List<MediaTagAssignment>>

    @Query(
        """
        SELECT
            tags.id AS id,
            tags.`key` AS `key`,
            tags.label AS label,
            tags.colorHex AS colorHex,
            tags.iconName AS iconName,
            COUNT(DISTINCT CASE
                WHEN media_items.batchGroupId != '' THEN media_items.batchGroupId
                ELSE 'legacy-' || media_items.id
            END) AS usageCount
        FROM tags
        INNER JOIN media_tags ON tags.id = media_tags.tagId
        INNER JOIN media_items ON media_items.id = media_tags.mediaId
        WHERE media_items.isTrashed = 0 AND media_items.isSecret = 0
        GROUP BY tags.id, tags.`key`, tags.label, tags.colorHex, tags.iconName
        ORDER BY usageCount DESC, tags.label ASC
        LIMIT :limit
        """,
    )
    fun observeTopTags(limit: Int = 10): Flow<List<TagUsageSummary>>

    @Query(
        """
        SELECT
            tags.id AS id,
            tags.`key` AS `key`,
            tags.label AS label,
            tags.colorHex AS colorHex,
            tags.iconName AS iconName,
            COUNT(DISTINCT CASE
                WHEN media_items.batchGroupId != '' THEN media_items.batchGroupId
                ELSE 'legacy-' || media_items.id
            END) AS usageCount
        FROM tags
        INNER JOIN media_tags ON tags.id = media_tags.tagId
        INNER JOIN media_items ON media_items.id = media_tags.mediaId
        WHERE media_items.isTrashed = 0
          AND media_items.isSecret = 0
          AND media_items.space = :space
        GROUP BY tags.id, tags.`key`, tags.label, tags.colorHex, tags.iconName
        ORDER BY usageCount DESC, tags.label ASC
        LIMIT :limit
        """,
    )
    fun observeTopTagsForSpace(space: MediaSpace, limit: Int = 10): Flow<List<TagUsageSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceMediaTags(crossRefs: List<MediaTagCrossRef>)

    @Transaction
    suspend fun setMediaTags(mediaId: Long, tagIds: List<Long>) {
        clearMediaTags(mediaId)
        if (tagIds.isNotEmpty()) {
            replaceMediaTags(tagIds.distinct().map { tagId -> MediaTagCrossRef(mediaId = mediaId, tagId = tagId) })
        }
    }

    @Query("DELETE FROM media_tags WHERE mediaId = :mediaId")
    suspend fun clearMediaTags(mediaId: Long)

    @Query("DELETE FROM media_tags WHERE tagId = :tagId")
    suspend fun clearTagAssignments(tagId: Long)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: Long)

    @Transaction
    suspend fun deleteManagedTag(tagId: Long) {
        clearTagAssignments(tagId)
        deleteTagById(tagId)
    }
}
