package com.jasonkang.memorix.core.media

import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageLayoutTest {
    @Test
    fun originalsDir_usesYearAndMonthFolders() {
        val root = File("/tmp/memorix-test")
        val now = LocalDateTime.of(2026, 5, 17, 9, 30)

        val result = StorageLayout.originalsDir(root, now)

        assertEquals("/tmp/memorix-test/originals/2026/05", result.path)
    }

    @Test
    fun originalFile_keepsProvidedExtension() {
        val root = File("/tmp/memorix-test")

        val result = StorageLayout.originalFile(
            root = root,
            extension = "mp4",
            uuid = "sample-id",
            now = LocalDateTime.of(2026, 5, 17, 9, 30),
        )

        assertTrue(result.path.endsWith("/originals/2026/05/sample-id.mp4"))
    }

    @Test
    fun thumbFile_isPlacedUnderThumbDirectory() {
        val root = File("/tmp/memorix-test")

        val result = StorageLayout.thumbFile(
            root = root,
            uuid = "thumb-id",
            now = LocalDateTime.of(2026, 5, 17, 9, 30),
        )

        assertTrue(result.path.endsWith("/thumbs/2026/05/thumb-id.jpg"))
    }
}
