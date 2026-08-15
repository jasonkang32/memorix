package com.mebonsoft.memorix.feature.work.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeMediaPreviewSupportTest {
    @Test
    fun `preview labels keep selected media in vertical display order`() {
        val rows = buildComposeMediaPreviewRows(selectedCount = 3)

        assertEquals(listOf("1/3", "2/3", "3/3"), rows.map { it.orderLabel })
        assertEquals(listOf(0, 1, 2), rows.map { it.sourceIndex })
    }

    @Test
    fun `large selections show only lightweight preview subset`() {
        val rows = buildComposeMediaPreviewRows(selectedCount = 30)

        assertEquals(6, rows.size)
        assertEquals("1/30", rows.first().orderLabel)
        assertEquals("6/30", rows.last().orderLabel)
        assertEquals(24, hiddenComposeMediaPreviewCount(30))
    }
}
