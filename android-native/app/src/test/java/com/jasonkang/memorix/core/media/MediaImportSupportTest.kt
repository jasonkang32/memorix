package com.jasonkang.memorix.core.media

import com.jasonkang.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaImportSupportTest {
    @Test
    fun inferMediaType_detectsPhotoFromMimeType() {
        val result = MediaImportSupport.inferMediaType(
            mimeType = "image/jpeg",
            displayName = null,
        )

        assertEquals(MediaType.PHOTO, result)
    }

    @Test
    fun inferMediaType_detectsVideoFromFileExtension() {
        val result = MediaImportSupport.inferMediaType(
            mimeType = null,
            displayName = "clip.mov",
        )

        assertEquals(MediaType.VIDEO, result)
    }

    @Test
    fun inferMediaType_defaultsToDocumentForPdf() {
        val result = MediaImportSupport.inferMediaType(
            mimeType = "application/pdf",
            displayName = "manual.pdf",
        )

        assertEquals(MediaType.DOCUMENT, result)
    }

    @Test
    fun inferExtension_prefersDisplayNameExtension() {
        val result = MediaImportSupport.inferExtension(
            mimeType = "image/jpeg",
            displayName = "holiday.png",
            mediaType = MediaType.PHOTO,
        )

        assertEquals("png", result)
    }

    @Test
    fun inferExtension_fallsBackToMimeTypeWhenNameMissing() {
        val result = MediaImportSupport.inferExtension(
            mimeType = "video/quicktime",
            displayName = null,
            mediaType = MediaType.VIDEO,
        )

        assertEquals("mov", result)
    }

    @Test
    fun inferExtension_fallsBackToMediaTypeDefault() {
        val result = MediaImportSupport.inferExtension(
            mimeType = null,
            displayName = null,
            mediaType = MediaType.DOCUMENT,
        )

        assertEquals("bin", result)
    }

    @Test
    fun inferTitle_stripsFileExtension() {
        val result = MediaImportSupport.inferTitle("receipt.pdf")

        assertEquals("receipt", result)
    }

    @Test
    fun inferTitle_returnsFallbackWhenNameMissing() {
        val result = MediaImportSupport.inferTitle(null)

        assertEquals("가져온 항목", result)
    }

    @Test
    fun inferTitle_returnsFallbackWhenNameIsBlankAfterTrim() {
        val result = MediaImportSupport.inferTitle("   ")

        assertEquals("가져온 항목", result)
    }
}
