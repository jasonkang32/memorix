package com.mebonsoft.memorix.feature.home

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import java.time.Instant
import java.time.ZoneId

internal data class HomeSummary(
    val totalCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val documentCount: Int,
    val albumEstimate: Int,
    val workCount: Int,
    val personalCount: Int,
    val workPhotoCount: Int,
    val workVideoCount: Int,
    val personalPhotoCount: Int,
    val personalVideoCount: Int,
)

internal fun calculateHomeSummary(items: List<MediaItemEntity>): HomeSummary {
    val photoCount = items.count { it.mediaType == MediaType.PHOTO }
    val videoCount = items.count { it.mediaType == MediaType.VIDEO }
    val documentCount = items.count { it.mediaType == MediaType.DOCUMENT }
    val workItems = items.filter { it.space == MediaSpace.WORK }
    val personalItems = items.filter { it.space == MediaSpace.PERSONAL }
    val albumEstimate = items.map { Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1) }
        .distinct()
        .size

    return HomeSummary(
        totalCount = items.size,
        photoCount = photoCount,
        videoCount = videoCount,
        documentCount = documentCount,
        albumEstimate = albumEstimate,
        workCount = countRegisteredGroups(workItems),
        personalCount = countRegisteredGroups(personalItems),
        workPhotoCount = workItems.count { it.mediaType == MediaType.PHOTO },
        workVideoCount = workItems.count { it.mediaType == MediaType.VIDEO },
        personalPhotoCount = personalItems.count { it.mediaType == MediaType.PHOTO },
        personalVideoCount = personalItems.count { it.mediaType == MediaType.VIDEO },
    )
}

private fun countRegisteredGroups(items: List<MediaItemEntity>): Int = items
    .map { item -> item.batchGroupId.ifBlank { "legacy-${item.id}" } }
    .distinct()
    .size
