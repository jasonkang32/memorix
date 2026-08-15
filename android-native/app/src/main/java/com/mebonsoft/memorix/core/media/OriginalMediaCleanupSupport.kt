package com.mebonsoft.memorix.core.media

import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType

enum class OriginalSourceCleanupStatus {
    UNKNOWN,
    AVAILABLE,
    DELETED,
    UNAVAILABLE,
    NEEDS_CONFIRMATION,
    FAILED,
}

data class OriginalSourceCandidate(
    val id: Long,
    val mediaType: MediaType,
    val sourceUri: String,
    val sourceSizeKb: Long,
    val cleanupStatus: OriginalSourceCleanupStatus,
    val space: MediaSpace,
)

data class OriginalCleanupSummary(
    val cleanableCount: Int = 0,
    val cleanableBytes: Long = 0L,
    val cleanableIds: List<Long> = emptyList(),
)

object OriginalMediaCleanupSupport {
    fun summarize(items: List<OriginalSourceCandidate>): OriginalCleanupSummary {
        val cleanable = items.filter(::isCleanable)
        return OriginalCleanupSummary(
            cleanableCount = cleanable.size,
            cleanableBytes = cleanable.sumOf { it.sourceSizeKb.coerceAtLeast(0L) * 1024L },
            cleanableIds = cleanable.map { it.id },
        )
    }

    fun isCleanable(candidate: OriginalSourceCandidate): Boolean =
        candidate.cleanupStatus != OriginalSourceCleanupStatus.DELETED &&
            isMediaStoreVisualSource(candidate.sourceUri, candidate.mediaType)

    fun isMediaStoreVisualSource(sourceUri: String, mediaType: MediaType): Boolean {
        if (mediaType != MediaType.PHOTO && mediaType != MediaType.VIDEO) return false
        if (!sourceUri.startsWith("content://media/")) return false
        return sourceUri.contains("/images/") || sourceUri.contains("/video/")
    }

    fun importStatusFor(sourceUri: String, mediaType: MediaType): OriginalSourceCleanupStatus =
        if (isMediaStoreVisualSource(sourceUri, mediaType)) {
            OriginalSourceCleanupStatus.AVAILABLE
        } else if (sourceUri.isBlank()) {
            OriginalSourceCleanupStatus.UNKNOWN
        } else {
            OriginalSourceCleanupStatus.UNAVAILABLE
        }
}
