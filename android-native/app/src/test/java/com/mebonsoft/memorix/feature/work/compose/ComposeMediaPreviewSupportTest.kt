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
}
