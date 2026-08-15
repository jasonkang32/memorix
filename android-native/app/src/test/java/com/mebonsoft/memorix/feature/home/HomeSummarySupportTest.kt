package com.mebonsoft.memorix.feature.home

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummarySupportTest {
    @Test
    fun calculateHomeSummary_countsRegisteredGroupsSeparatelyFromPhotoAndVideoFiles() {
        val firstWorkRegistration = "work-registration-1"
        val secondWorkRegistration = "work-registration-2"
        val personalRegistration = "personal-registration-1"
        val items = listOf(
            mediaItem(id = 1L, space = MediaSpace.WORK, mediaType = MediaType.PHOTO, batchGroupId = firstWorkRegistration),
            mediaItem(id = 2L, space = MediaSpace.WORK, mediaType = MediaType.PHOTO, batchGroupId = firstWorkRegistration),
            mediaItem(id = 3L, space = MediaSpace.WORK, mediaType = MediaType.VIDEO, batchGroupId = firstWorkRegistration),
            mediaItem(id = 4L, space = MediaSpace.WORK, mediaType = MediaType.PHOTO, batchGroupId = secondWorkRegistration),
            mediaItem(id = 5L, space = MediaSpace.PERSONAL, mediaType = MediaType.PHOTO, batchGroupId = personalRegistration),
            mediaItem(id = 6L, space = MediaSpace.PERSONAL, mediaType = MediaType.VIDEO, batchGroupId = personalRegistration),
        )

        val summary = calculateHomeSummary(items)

        assertEquals(2, summary.workCount)
        assertEquals(1, summary.personalCount)
        assertEquals(3, summary.workPhotoCount)
        assertEquals(1, summary.workVideoCount)
        assertEquals(1, summary.personalPhotoCount)
        assertEquals(1, summary.personalVideoCount)
    }

    @Test
    fun calculateHomeSummary_treatsLegacyItemsWithoutBatchGroupAsIndividualRegistrations() {
        val items = listOf(
            mediaItem(id = 1L, space = MediaSpace.WORK, mediaType = MediaType.PHOTO, batchGroupId = ""),
            mediaItem(id = 2L, space = MediaSpace.WORK, mediaType = MediaType.PHOTO, batchGroupId = ""),
        )

        val summary = calculateHomeSummary(items)

        assertEquals(2, summary.workCount)
        assertEquals(2, summary.workPhotoCount)
    }

    @Test
    fun buildRecentActivityBuckets_returnsThirtyDateBucketsWithCounts() {
        val today = LocalDate.of(2026, 7, 29)
        val yesterday = today.minusDays(1)
        val oldestVisible = today.minusDays(29)
        val olderThanVisible = today.minusDays(30)
        val items = listOf(
            mediaItem(id = 10L, takenAt = today.toEpochMillis()),
            mediaItem(id = 11L, takenAt = today.toEpochMillis()),
            mediaItem(id = 12L, takenAt = yesterday.toEpochMillis()),
            mediaItem(id = 13L, takenAt = oldestVisible.toEpochMillis()),
            mediaItem(id = 14L, takenAt = olderThanVisible.toEpochMillis()),
        )

        val buckets = buildRecentActivityBuckets(items = items, today = today)

        assertEquals(30, buckets.size)
        assertEquals(oldestVisible, buckets.first().date)
        assertEquals(1, buckets.first().count)
        assertEquals(1, buckets[28].count)
        assertEquals(today, buckets.last().date)
        assertEquals(2, buckets.last().count)
    }

    private fun mediaItem(
        id: Long,
        space: MediaSpace = MediaSpace.WORK,
        mediaType: MediaType = MediaType.PHOTO,
        batchGroupId: String = "registration-$id",
        takenAt: Long = 1_725_600_000_000L,
    ): MediaItemEntity = MediaItemEntity(
        id = id,
        space = space,
        mediaType = mediaType,
        filePath = "/tmp/$id",
        takenAt = takenAt,
        createdAt = takenAt,
        fileSizeKb = 100L,
        batchGroupId = batchGroupId,
    )

    private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
