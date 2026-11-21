package com.mebonsoft.memorix.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mebonsoft.memorix.core.media.OriginalSourceCleanupStatus

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val space: MediaSpace = MediaSpace.WORK,
    val mediaType: MediaType,
    val filePath: String,
    val thumbPath: String? = null,
    val title: String = "",
    val note: String = "",
    val albumId: Long? = null,
    val takenAt: Long,
    val createdAt: Long,
    val fileSizeKb: Long = 0,
    val durationSec: Long = 0,
    val mimeType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val countryCode: String = "",
    val region: String = "",
    val batchGroupId: String = "",
    val ocrText: String = "",
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    // 비밀: 목록에서 숨기고 설정 > 비밀 보관함에서만 본다.
    val isSecret: Boolean = false,
    // 잠금: 목록에는 보이지만 미리보기를 가리고, 열 때 생체인증/PIN을 요구한다.
    val isLocked: Boolean = false,
    val sourceUri: String = "",
    val sourceDisplayName: String = "",
    val sourceSizeKb: Long = 0,
    val sourceDeletedAt: Long? = null,
    val sourceCleanupStatus: OriginalSourceCleanupStatus = OriginalSourceCleanupStatus.UNKNOWN,
    val sourceCleanupError: String = "",
)
