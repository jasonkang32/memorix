package com.jasonkang.memorix.feature.detail

import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaEditorSupportTest {
    @Test
    fun applyDraft_updatesEditableFields() {
        val original = MediaItemEntity(
            id = 7,
            mediaType = MediaType.PHOTO,
            filePath = "/tmp/a.jpg",
            title = "old",
            note = "memo",
            albumId = 1,
            takenAt = 1000L,
            createdAt = 1000L,
        )

        val updated = MediaEditorSupport.applyDraft(
            item = original,
            title = "  new title  ",
            note = "  new memo  ",
            albumId = 3,
            takenAt = 9999L,
            isFavorite = true,
        )

        assertEquals("new title", updated.title)
        assertEquals("new memo", updated.note)
        assertEquals(3L, updated.albumId)
        assertEquals(9999L, updated.takenAt)
        assertEquals(true, updated.isFavorite)
        assertEquals(original.filePath, updated.filePath)
    }
}
