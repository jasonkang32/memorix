package com.mebonsoft.memorix.feature.detail

import com.mebonsoft.memorix.core.database.entity.MediaType
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
}
