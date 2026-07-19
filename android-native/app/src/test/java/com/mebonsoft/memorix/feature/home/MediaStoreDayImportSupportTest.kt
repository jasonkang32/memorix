package com.mebonsoft.memorix.feature.home

import com.mebonsoft.memorix.core.database.entity.MediaSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MediaStoreDayImportSupportTest {
    @Test
    fun selectedDayRange_returnsSameStartAndEndDateForOneSelectedDay() {
        val zoneId = ZoneId.of("Asia/Seoul")
        val selectedMillis = Instant.parse("2026-08-06T03:30:00Z").toEpochMilli()

        val range = MediaStoreDayImportSupport.selectedDayRange(selectedMillis, zoneId)

        assertEquals(LocalDate.of(2026, 8, 6), range?.startDate)
        assertEquals(LocalDate.of(2026, 8, 6), range?.endDate)
        assertEquals(1L, range?.dayCount)
    }

    @Test
    fun selectedDayRange_returnsNullWhenNoDateWasSelected() {
        assertNull(MediaStoreDayImportSupport.selectedDayRange(null, ZoneId.of("Asia/Seoul")))
    }

    @Test
    fun dayImportRequest_keepsSelectedDayAndDestinationSpaceForImmediateImport() {
        val zoneId = ZoneId.of("Asia/Seoul")
        val selectedMillis = Instant.parse("2026-08-06T03:30:00Z").toEpochMilli()

        val request = MediaStoreDayImportSupport.dayImportRequest(
            selectedMillis = selectedMillis,
            space = MediaSpace.PERSONAL,
            zoneId = zoneId,
        )

        assertEquals(LocalDate.of(2026, 8, 6), request?.selectedDate)
        assertEquals(MediaSpace.PERSONAL, request?.space)
    }

    @Test
    fun dayImportRequest_returnsNullWhenNoDateWasSelected() {
        assertNull(
            MediaStoreDayImportSupport.dayImportRequest(
                selectedMillis = null,
                space = MediaSpace.WORK,
                zoneId = ZoneId.of("Asia/Seoul"),
            )
        )
    }

    @Test
    fun displayName_describesSingleDayBulkImport() {
        assertEquals("날짜 하루", HomeQuickImportAction.DATE_RANGE.title)
        assertEquals("선택한 하루의 사진·영상을 한 번에 등록합니다.", HomeQuickImportAction.DATE_RANGE.description)
    }
}
