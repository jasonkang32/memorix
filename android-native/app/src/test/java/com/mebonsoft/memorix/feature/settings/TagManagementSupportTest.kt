package com.mebonsoft.memorix.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagManagementSupportTest {
    @Test
    fun visibleTags_areSortedByLabelAndExposeUsageText() {
        val rows = TagManagementSupport.sortedRows(
            listOf(
                ManagedTag(id = 2, label = "영수증", usageCount = 3),
                ManagedTag(id = 1, label = "계약서", usageCount = 0),
            )
        )

        assertEquals(listOf("계약서", "영수증"), rows.map { it.label })
        assertEquals("미사용", rows[0].usageLabel)
        assertEquals("3개 기록", rows[1].usageLabel)
    }

    @Test
    fun deleteWarning_explainsThatMediaRecordsRemainAndOnlyTagLinksAreRemoved() {
        val warning = TagManagementSupport.deleteWarning(ManagedTag(id = 7, label = "영수증", usageCount = 4))

        assertTrue(warning.contains("영수증"))
        assertTrue(warning.contains("4개 기록"))
        assertTrue(warning.contains("미디어 기록은 삭제되지 않습니다"))
        assertTrue(warning.contains("태그 연결만 제거"))
    }

    @Test
    fun emptyTagListMessage_describesWhyTagManagementExists() {
        val message = TagManagementSupport.emptyMessage

        assertTrue(message.contains("중복"))
        assertTrue(message.contains("태그"))
        assertFalse(message.contains("각 기록 수정 화면"))
    }
}
