package com.mebonsoft.memorix.core.media

import com.mebonsoft.memorix.core.database.entity.MediaType
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateRangeImportSupportTest {
    private val zoneId = ZoneOffset.UTC

    @Test
    fun importDateRange_containsStartAndEndDayInclusively() {
        val range = ImportDateRange(
            startDate = LocalDate.of(2024, 5, 10),
            endDate = LocalDate.of(2024, 5, 12),
        )

        assertTrue(range.contains(epochMillisOf(2024, 5, 10, 0, 0), zoneId))
        assertTrue(range.contains(epochMillisOf(2024, 5, 12, 23, 59), zoneId))
        assertFalse(range.contains(epochMillisOf(2024, 5, 9, 23, 59), zoneId))
        assertFalse(range.contains(epochMillisOf(2024, 5, 13, 0, 0), zoneId))
    }

    @Test
    fun importDateRange_reportsInclusiveDayCount() {
        val range = ImportDateRange(
            startDate = LocalDate.of(2024, 1, 30),
            endDate = LocalDate.of(2024, 2, 2),
        )

        assertEquals(4L, range.dayCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importDateRange_rejectsReversedRange() {
        ImportDateRange(
            startDate = LocalDate.of(2024, 5, 12),
            endDate = LocalDate.of(2024, 5, 10),
        )
    }

    @Test
    fun selectCandidates_filtersMissingDatesAndSortsNewestFirst() {
        val range = ImportDateRange(
            startDate = LocalDate.of(2024, 5, 10),
            endDate = LocalDate.of(2024, 5, 12),
        )
        val selected = DateRangeImportSupport.selectCandidates(
            candidates = listOf(
                candidate(
                    sourceId = "late-in-day",
                    takenAt = epochMillisOf(2024, 5, 12, 22, 30),
                    mediaType = MediaType.VIDEO,
                    fileSizeKb = 4_096,
                ),
                candidate(
                    sourceId = "missing-date",
                    takenAt = null,
                    mediaType = MediaType.PHOTO,
                ),
                candidate(
                    sourceId = "out-of-range",
                    takenAt = epochMillisOf(2024, 5, 9, 8, 0),
                    mediaType = MediaType.PHOTO,
                ),
                candidate(
                    sourceId = "older-in-range",
                    takenAt = epochMillisOf(2024, 5, 10, 9, 15),
                    mediaType = MediaType.PHOTO,
                    fileSizeKb = 512,
                ),
                candidate(
                    sourceId = "same-time-b",
                    takenAt = epochMillisOf(2024, 5, 11, 8, 0),
                    mediaType = MediaType.DOCUMENT,
                    fileSizeKb = 128,
                ),
                candidate(
                    sourceId = "same-time-a",
                    takenAt = epochMillisOf(2024, 5, 11, 8, 0),
                    mediaType = MediaType.PHOTO,
                    fileSizeKb = 256,
                ),
            ),
            range = range,
            zoneId = zoneId,
        )

        assertEquals(
            listOf("late-in-day", "same-time-a", "same-time-b", "older-in-range"),
            selected.map { it.sourceId },
        )
    }

    @Test
    fun summarizeSelection_aggregatesCountDatesSizeAndMediaTypes() {
        val selected = listOf(
            candidate(
                sourceId = "video",
                takenAt = epochMillisOf(2024, 5, 12, 22, 30),
                mediaType = MediaType.VIDEO,
                fileSizeKb = 4_096,
            ),
            candidate(
                sourceId = "photo",
                takenAt = epochMillisOf(2024, 5, 10, 9, 15),
                mediaType = MediaType.PHOTO,
                fileSizeKb = 512,
            ),
            candidate(
                sourceId = "document",
                takenAt = epochMillisOf(2024, 5, 11, 8, 0),
                mediaType = MediaType.DOCUMENT,
                fileSizeKb = 128,
            ),
        )

        val summary = DateRangeImportSupport.summarizeSelection(
            selected = selected,
            zoneId = zoneId,
        )

        assertEquals(3, summary.totalCount)
        assertEquals(4_736L, summary.totalSizeKb)
        assertEquals(LocalDate.of(2024, 5, 10), summary.oldestDate)
        assertEquals(LocalDate.of(2024, 5, 12), summary.newestDate)
        assertEquals(1, summary.countByType.getValue(MediaType.PHOTO))
        assertEquals(1, summary.countByType.getValue(MediaType.VIDEO))
        assertEquals(1, summary.countByType.getValue(MediaType.DOCUMENT))
    }

    private fun candidate(
        sourceId: String,
        takenAt: Long?,
        mediaType: MediaType,
        fileSizeKb: Long = 0,
    ) = ImportCandidateMetadata(
        sourceId = sourceId,
        displayName = "$sourceId.jpg",
        takenAtEpochMillis = takenAt,
        mediaType = mediaType,
        fileSizeKb = fileSizeKb,
    )

    private fun epochMillisOf(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDate.of(year, month, day)
        .atTime(hour, minute)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
}
