package com.mebonsoft.memorix.core.media

import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalMediaCleanupSupportTest {
    @Test
    fun cleanupSummaryCountsOnlyMediaStorePhotoAndVideoSourcesThatAreNotDeleted() {
        val items = listOf(
            source(id = 1, type = MediaType.PHOTO, sourceUri = "content://media/external/images/media/10", sizeKb = 1024),
            source(id = 2, type = MediaType.VIDEO, sourceUri = "content://media/external/video/media/20", sizeKb = 2048),
            source(id = 3, type = MediaType.DOCUMENT, sourceUri = "content://media/external/file/30", sizeKb = 4096),
            source(id = 4, type = MediaType.PHOTO, sourceUri = "content://com.example.provider/item/1", sizeKb = 512),
            source(id = 5, type = MediaType.PHOTO, sourceUri = "content://media/external/images/media/50", sizeKb = 256, status = OriginalSourceCleanupStatus.DELETED),
            source(id = 6, type = MediaType.PHOTO, sourceUri = "", sizeKb = 999),
        )

        val summary = OriginalMediaCleanupSupport.summarize(items)

        assertEquals(2, summary.cleanableCount)
        assertEquals(3_145_728L, summary.cleanableBytes)
        assertEquals(listOf(1L, 2L), summary.cleanableIds)
    }

    @Test
    fun mediaStoreVisualSourceRequiresMediaAuthorityAndPhotoOrVideo() {
        assertTrue(OriginalMediaCleanupSupport.isMediaStoreVisualSource("content://media/external/images/media/10", MediaType.PHOTO))
        assertTrue(OriginalMediaCleanupSupport.isMediaStoreVisualSource("content://media/external/video/media/20", MediaType.VIDEO))
        assertFalse(OriginalMediaCleanupSupport.isMediaStoreVisualSource("content://media/external/file/30", MediaType.DOCUMENT))
        assertFalse(OriginalMediaCleanupSupport.isMediaStoreVisualSource("content://com.example.provider/item/1", MediaType.PHOTO))
        assertFalse(OriginalMediaCleanupSupport.isMediaStoreVisualSource("", MediaType.PHOTO))
    }

    private fun source(
        id: Long,
        type: MediaType,
        sourceUri: String,
        sizeKb: Long,
        status: OriginalSourceCleanupStatus = OriginalSourceCleanupStatus.AVAILABLE,
    ) = OriginalSourceCandidate(
        id = id,
        mediaType = type,
        sourceUri = sourceUri,
        sourceSizeKb = sizeKb,
        cleanupStatus = status,
        space = MediaSpace.WORK,
    )
}
