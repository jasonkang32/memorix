package com.mebonsoft.memorix.feature.albums

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumEditorSupportTest {
    @Test
    fun sanitizedTitle_trimsWhitespace() {
        assertEquals("여름 여행", AlbumEditorSupport.sanitizedTitle("  여름 여행  "))
    }

    @Test
    fun sanitizedTitle_fallsBackWhenBlank() {
        assertEquals("새 앨범", AlbumEditorSupport.sanitizedTitle("   "))
    }
}
