package com.mebonsoft.memorix.feature.work.timeline

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkTimelineTest {
    @Test
    fun timelineLazyKeys_areUniqueWhenSameBatchSpansDifferentDates() {
        val oneDay = 24 * 60 * 60 * 1000L
        val items = listOf(
            mediaItem(id = 1L, takenAt = oneDay, batchGroupId = "same-batch"),
            mediaItem(id = 2L, takenAt = oneDay * 2, batchGroupId = "same-batch"),
        )

        val sections = buildSections(items)
        val keys = sections.flatMap { section ->
            section.groups.map { group -> timelineGroupLazyKey(section, group) }
        }

        assertEquals(1, sections.size)
        assertEquals(listOf(2L, 1L), sections.single().groups.single().items.map(MediaItemEntity::id))
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun buildSections_groupsItemsByBatchGroupIdInsideDate() {
        val takenAt = 1_800_000L
        val items = listOf(
            mediaItem(id = 1L, takenAt = takenAt, batchGroupId = "batch-1"),
            mediaItem(id = 2L, takenAt = takenAt + 1_000L, batchGroupId = "batch-1"),
            mediaItem(id = 3L, takenAt = takenAt + 2_000L, batchGroupId = "batch-2"),
        )

        val groups = buildSections(items).single().groups

        assertEquals(listOf(listOf(3L), listOf(2L, 1L)), groups.map { it.items.map(MediaItemEntity::id) })
    }

    @Test
    fun buildSections_ordersGroupsByLatestRegistrationTime() {
        val olderCreatedButNewPhoto = mediaItem(id = 1L, takenAt = 3_000L, createdAt = 10_000L, batchGroupId = "old-register")
        val latestCreatedButOldPhoto = mediaItem(id = 2L, takenAt = 1_000L, createdAt = 20_000L, batchGroupId = "new-register")

        val groups = buildSections(listOf(olderCreatedButNewPhoto, latestCreatedButOldPhoto))
            .flatMap { it.groups }

        assertEquals(listOf("new-register", "old-register"), groups.map { it.key })
    }

    @Test
    fun buildSections_whenEventTimeSortMode_ordersGroupsByLatestEventTime() {
        val latestRegisteredButOldEvent = mediaItem(id = 1L, takenAt = 1_000L, createdAt = 20_000L, batchGroupId = "latest-register")
        val olderRegisteredButNewEvent = mediaItem(id = 2L, takenAt = 30_000L, createdAt = 10_000L, batchGroupId = "latest-event")

        val groups = buildSections(
            listOf(latestRegisteredButOldEvent, olderRegisteredButNewEvent),
            sortMode = TimelineSortMode.EVENT_TIME,
        ).flatMap { it.groups }

        assertEquals(listOf("latest-event", "latest-register"), groups.map { it.key })
    }

    @Test
    fun buildSections_defaultRegistrationSortMode_groupsSectionsByRegistrationDate() {
        val registeredTodayButOldEvent = mediaItem(
            id = 1L,
            takenAt = 1_000L,
            createdAt = 86_400_000L,
            batchGroupId = "registered-today",
        )

        val section = buildSections(listOf(registeredTodayButOldEvent)).single()

        assertEquals("1970년 1월 2일", section.dateLabel)
    }

    private fun mediaItem(
        id: Long,
        takenAt: Long,
        note: String = "",
        region: String = "",
        batchGroupId: String = "",
        createdAt: Long = takenAt,
    ) = MediaItemEntity(
        id = id,
        space = MediaSpace.WORK,
        mediaType = MediaType.PHOTO,
        filePath = "/tmp/$id.jpg",
        note = note,
        takenAt = takenAt,
        createdAt = createdAt,
        region = region,
        batchGroupId = batchGroupId,
    )
}
