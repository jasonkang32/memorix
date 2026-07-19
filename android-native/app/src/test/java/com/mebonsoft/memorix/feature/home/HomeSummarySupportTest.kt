package com.mebonsoft.memorix.feature.home

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
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

    private fun mediaItem(
        id: Long,
        space: MediaSpace,
        mediaType: MediaType,
        batchGroupId: String,
    ): MediaItemEntity = MediaItemEntity(
        id = id,
        space = space,
        mediaType = mediaType,
        filePath = "/tmp/$id",
        takenAt = 1_725_600_000_000L,
        createdAt = 1_725_600_000_000L,
        fileSizeKb = 100L,
        batchGroupId = batchGroupId,
    )
}
