package com.jasonkang.memorix.feature.search

import com.jasonkang.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSupportTest {
    @Test
    fun toFtsQuery_trimsAndAddsPrefixSearch() {
        assertEquals("summer* memory*", SearchSupport.toFtsQuery("  summer   memory  "))
    }

    @Test
    fun toFtsQuery_returnsNullForBlank() {
        assertNull(SearchSupport.toFtsQuery("   "))
    }

    @Test
    fun chipLabel_usesKoreanLabels() {
        assertEquals("전체", SearchSupport.mediaTypeLabel(null))
        assertEquals("사진", SearchSupport.mediaTypeLabel(MediaType.PHOTO))
        assertEquals("영상", SearchSupport.mediaTypeLabel(MediaType.VIDEO))
        assertEquals("문서", SearchSupport.mediaTypeLabel(MediaType.DOCUMENT))
    }
}
