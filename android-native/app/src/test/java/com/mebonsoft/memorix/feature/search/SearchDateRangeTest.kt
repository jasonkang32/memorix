package com.mebonsoft.memorix.feature.search

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SearchDateRangeTest {
    private val korea = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 11, 8)

    @Test fun includesWholeSelectedDayUsingDeviceLocalDate() {
        val range = SearchDateRange(day, day)
        assertTrue(range.contains(Instant.parse("2026-11-07T15:00:00Z").toEpochMilli(), korea))
        assertTrue(range.contains(Instant.parse("2026-11-08T14:59:59.999Z").toEpochMilli(), korea))
        assertFalse(range.contains(Instant.parse("2026-11-07T14:59:59.999Z").toEpochMilli(), korea))
        assertFalse(range.contains(Instant.parse("2026-11-08T15:00:00Z").toEpochMilli(), korea))
    }

    @Test fun supportsOpenEndedDatesAndRejectsReversedRange() {
        val timestamp = day.atStartOfDay(korea).toInstant().toEpochMilli()
        assertTrue(SearchDateRange().contains(timestamp, korea))
        assertFalse(SearchDateRange().isActive)
        assertTrue(SearchDateRange(from = day).isActive)
        assertTrue(SearchDateRange(from = day).contains(timestamp, korea))
        assertFalse(SearchDateRange(from = day.plusDays(1)).contains(timestamp, korea))
        assertTrue(SearchDateRange(to = day).contains(timestamp, korea))
        assertFalse(SearchDateRange(to = day.minusDays(1)).contains(timestamp, korea))
        assertFalse(SearchDateRange(day, day.minusDays(1)).isValid)
        assertFalse(SearchDateRange(day, day.minusDays(1)).contains(timestamp, korea))
    }
}
