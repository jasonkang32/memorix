package com.mebonsoft.memorix.feature.detail

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDetailPreviewSupportTest {
    @Test
    fun `fullscreen preview is available for photos and videos only`() {
        assertTrue(isFullscreenPreviewAvailable(MediaType.PHOTO))
        assertTrue(isFullscreenPreviewAvailable(MediaType.VIDEO))
        assertFalse(isFullscreenPreviewAvailable(MediaType.DOCUMENT))
    }

    @Test
    fun `fullscreen photo viewer uses only photos from current work group`() {
        val first = media(id = 1, mediaType = MediaType.PHOTO)
        val video = media(id = 2, mediaType = MediaType.VIDEO)
        val second = media(id = 3, mediaType = MediaType.PHOTO)
        val document = media(id = 4, mediaType = MediaType.DOCUMENT)

        val result = fullscreenPreviewItems(
            selectedItem = second,
            relatedItems = listOf(first, video, second, document),
        )

        assertEquals(listOf(1L, 3L), result.map { it.id })
        assertEquals(1, fullscreenInitialPage(selectedItemId = second.id, previewItems = result))
    }

    @Test
    fun `fullscreen video preview stays on selected video`() {
        val photo = media(id = 1, mediaType = MediaType.PHOTO)
        val video = media(id = 2, mediaType = MediaType.VIDEO)

        val result = fullscreenPreviewItems(
            selectedItem = video,
            relatedItems = listOf(photo, video),
        )

        assertEquals(listOf(2L), result.map { it.id })
        assertEquals(0, fullscreenInitialPage(selectedItemId = video.id, previewItems = result))
    }

    private fun media(id: Long, mediaType: MediaType): MediaItemEntity = MediaItemEntity(
        id = id,
        space = MediaSpace.WORK,
        mediaType = mediaType,
        filePath = "/tmp/$id",
        takenAt = 1_700_000_000_000,
        createdAt = 1_700_000_000_000,
    )
}
