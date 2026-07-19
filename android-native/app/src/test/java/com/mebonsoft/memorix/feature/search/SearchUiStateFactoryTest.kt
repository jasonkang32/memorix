package com.mebonsoft.memorix.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchUiStateFactoryTest {
    @Test
    fun createSummary_reflectsQueryAndCount() {
        val summary = SearchUiStateFactory.createSummary(
            query = "제주",
            resultCount = 3,
            albumTitle = "여행",
            mediaTypeLabel = "사진",
        )

        assertEquals("'제주' · 여행 · 사진 · 3건", summary)
    }

    @Test
    fun createSummary_usesFallbackWhenNoFilters() {
        val summary = SearchUiStateFactory.createSummary(
            query = "",
            resultCount = 0,
            albumTitle = null,
            mediaTypeLabel = "전체",
        )

        assertEquals("전체 미디어 · 0건", summary)
    }
}
