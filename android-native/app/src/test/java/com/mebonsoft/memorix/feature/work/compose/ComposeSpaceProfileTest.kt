package com.mebonsoft.memorix.feature.work.compose

import com.mebonsoft.memorix.core.database.entity.MediaSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeSpaceProfileTest {
    @Test
    fun `work profile focuses on simple record context`() {
        val profile = composeSpaceProfile(MediaSpace.WORK)

        assertEquals("Work 등록", profile.title)
        assertEquals("업무 관련 메모를 입력하세요...", profile.notePlaceholder)
        assertTrue(profile.quickSections.any { section -> section.title == "기록 종류" && section.labels.containsAll(listOf("회의", "요청", "결정")) })
        assertTrue(profile.quickSections.any { section -> section.title == "업무 맥락" && section.labels.containsAll(listOf("내부", "거래처", "현장")) })
        assertTrue(profile.quickSections.any { section -> section.title == "보관 이유" && section.labels.containsAll(listOf("참고용", "공유용", "확인용")) })
        assertTrue(profile.quickSections.none { section -> section.title in listOf("상태", "우선순위", "마감") })
        assertTrue(profile.quickSections.flatMap { it.labels }.none { label -> label in listOf("진행중", "완료", "긴급", "오늘 마감", "마감 임박") })
    }

    @Test
    fun `personal profile focuses on memory and feeling`() {
        val profile = composeSpaceProfile(MediaSpace.PERSONAL)

        assertEquals("Personal 등록", profile.title)
        assertEquals("오늘의 기억을 조용히 남겨보세요...", profile.notePlaceholder)
        assertTrue(profile.quickSections.any { section -> section.title == "감정" && section.labels.containsAll(listOf("좋음", "평온", "감사", "행복")) })
        assertTrue(profile.quickSections.any { section -> section.title == "함께한 사람" && section.labels.containsAll(listOf("가족", "친구", "동료")) })
        assertTrue(profile.quickSections.any { section -> section.title == "기억 종류" && section.labels.containsAll(listOf("여행", "음식", "건강", "일상")) })
    }
}
