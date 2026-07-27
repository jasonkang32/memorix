package com.mebonsoft.memorix.feature.detail

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
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

    @Test
    fun relatedWorkItems_returnsItemsFromSameTimelineGroup() {
        val selected = mediaItem(id = 1, takenAt = 3_600_000L, note = "회의 자료", region = "서울")
        val sibling = mediaItem(id = 2, takenAt = 3_610_000L, note = "회의 자료", region = "서울")
        val differentNote = mediaItem(id = 3, takenAt = 3_620_000L, note = "다른 메모", region = "서울")
        val differentHour = mediaItem(id = 4, takenAt = 7_200_000L, note = "회의 자료", region = "서울")

        val related = MediaEditorSupport.relatedWorkItems(
            selected = selected,
            candidates = listOf(differentHour, sibling, differentNote, selected),
        )

        assertEquals(listOf(1L, 2L), related.map { it.id })
    }

    @Test
    fun workDeleteTargets_returnsWholeWorkGroup() {
        val selected = mediaItem(id = 1, takenAt = 1_000L, note = "증빙", region = "서울")
        val sibling = mediaItem(id = 2, takenAt = 2_000L, note = "증빙", region = "서울")
        val trashed = mediaItem(id = 3, takenAt = 3_000L, note = "증빙", region = "서울", isTrashed = true)

        val targets = MediaEditorSupport.workDeleteTargets(
            selected = selected,
            relatedItems = listOf(selected, sibling, trashed, sibling),
        )

        assertEquals(listOf(1L, 2L), targets.map { it.id })
    }

    @Test
    fun workDeleteTargets_keepsPersonalDeleteSingleMediaOnly() {
        val selected = mediaItem(
            id = 1,
            takenAt = 1_000L,
            note = "개인",
            region = "서울",
            space = MediaSpace.PERSONAL,
        )
        val sibling = mediaItem(
            id = 2,
            takenAt = 2_000L,
            note = "개인",
            region = "서울",
            space = MediaSpace.PERSONAL,
        )

        val targets = MediaEditorSupport.workDeleteTargets(
            selected = selected,
            relatedItems = listOf(selected, sibling),
        )

        assertEquals(listOf(1L), targets.map { it.id })
    }
}

private fun mediaItem(
    id: Long,
    takenAt: Long,
    note: String,
    region: String,
    space: MediaSpace = MediaSpace.WORK,
    isTrashed: Boolean = false,
): MediaItemEntity = MediaItemEntity(
    id = id,
    space = space,
    mediaType = MediaType.PHOTO,
    filePath = "/tmp/$id.jpg",
    title = "item $id",
    note = note,
    region = region,
    takenAt = takenAt,
    createdAt = takenAt,
    isTrashed = isTrashed,
)
