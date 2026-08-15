package com.mebonsoft.memorix.feature.work.compose

import com.mebonsoft.memorix.core.database.entity.MediaSpace
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeSpaceProfileTest {
    @Test
    fun `work profile uses tag-only registration copy`() {
        val profile = composeSpaceProfile(MediaSpace.WORK)

        assertEquals("업무 미디어 등록", profile.title)
        assertEquals("업무 메모", profile.noteLabel)
        assertEquals("필요한 설명만 적고, 분류는 아래 태그로 관리하세요...", profile.notePlaceholder)
        assertEquals("태그", profile.tagLabel)
    }

    @Test
    fun `personal profile uses tag-only registration copy`() {
        val profile = composeSpaceProfile(MediaSpace.PERSONAL)

        assertEquals("개인 미디어 등록", profile.title)
        assertEquals("개인 메모", profile.noteLabel)
        assertEquals("오늘의 기억을 조용히 남기고, 분류는 아래 태그로 관리하세요...", profile.notePlaceholder)
        assertEquals("태그", profile.tagLabel)
    }
}
