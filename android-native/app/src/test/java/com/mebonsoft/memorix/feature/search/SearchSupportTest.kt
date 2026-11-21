package com.mebonsoft.memorix.feature.search

import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchSupportTest {
    private fun photo() = MediaItemEntity(
        id = 1, mediaType = MediaType.PHOTO, filePath = "/local/photo.jpg",
        title = "에어컨 교체", note = "설치 완료", ocrText = "MODEL-X100",
        countryCode = "대한민국", region = "서울 강남", sourceDisplayName = "IMG_20261108.jpg",
        takenAt = 0, createdAt = 0,
    )

    @Test
    fun localSearchFindsTitleLocationAndOriginalFileName() {
        val item = photo()
        listOf("에어컨", "대한민국", "강남", "20261108").forEach { query ->
            assertTrue("검색 대상: $query", SearchSupport.matchesLocal(item, query))
        }
    }

    @Test
    fun localSearchCombinesKeywordsAcrossFieldsAndTagsInAnyOrder() {
        assertTrue(SearchSupport.matchesLocal(photo(), "  강남   교체 #거래처 X100 ", listOf("거래처")))
        assertTrue(SearchSupport.matchesLocal(photo(), "완료 서울", listOf("거래처")))
        assertFalse(SearchSupport.matchesLocal(photo(), "강남 교체 수리전", listOf("거래처")))
        assertFalse(SearchSupport.matchesLocal(photo(), "서울 #현장", listOf("거래처")))
    }

    @Test
    fun localSearchTreatsPunctuationLiterallyAndIgnoresCase() {
        assertTrue(SearchSupport.matchesLocal(photo(), "model-x100"))
        assertFalse(SearchSupport.matchesLocal(photo(), "MODEL*"))
        assertTrue(SearchSupport.matchesLocal(photo(), " \n "))
    }

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
