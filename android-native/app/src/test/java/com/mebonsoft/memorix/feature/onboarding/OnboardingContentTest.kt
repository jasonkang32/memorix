package com.mebonsoft.memorix.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingContentTest {
    @Test
    fun pagesKeepFlutterOrderAndAddCurrentProMessage() {
        val pages = MemorixOnboardingContent.pages

        assertEquals(4, pages.size)
        assertEquals("내 사진, 밖에 안 나갑니다", pages[0].title)
        assertEquals("Work · Personal 완전 분리", pages[1].title)
        assertEquals("찾기는 더 똑똑하게", pages[2].title)
        assertEquals("공유는 자유롭게 Pro는 더 깔끔하게", pages[3].title)
        assertTrue(pages[3].description.contains("기본 사진 공유는 무료"))
        assertTrue(pages[3].description.contains("PDF"))
        assertTrue(pages[3].description.contains("백업"))
    }

    @Test
    fun proSummaryDoesNotMentionRegistrationLimit() {
        val allText = MemorixOnboardingContent.pages.joinToString("\n") { page ->
            "${page.title}\n${page.description}"
        }

        assertTrue(allText.contains("등록 수량 제한 없이"))
        assertTrue(!allText.contains("50개"))
        assertTrue(!allText.contains("300개"))
        assertTrue(!allText.contains("500개"))
    }
}
