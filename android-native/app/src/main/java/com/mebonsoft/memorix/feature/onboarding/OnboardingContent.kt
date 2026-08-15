package com.mebonsoft.memorix.feature.onboarding

import androidx.compose.ui.graphics.Color

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val gradientColors: List<Color>,
)

object MemorixOnboardingContent {
    val pages = listOf(
        OnboardingPage(
            emoji = "🔒",
            title = "내 사진, 밖에 안 나갑니다",
            description = "갤러리 대신 메모릭스에 보관하세요.\n등록 수량 제한 없이 내 폰 안에서 업무와 개인 기록을 조용히 정리합니다.",
            gradientColors = listOf(Color(0xFF00C896), Color(0xFF00897B)),
        ),
        OnboardingPage(
            emoji = "💼",
            title = "업무 · 개인 완전 분리",
            description = "업무 현장 사진과 개인 기록을\n하나의 앱에서 분리해 관리합니다.\n메모, 태그, 날짜로 나중에 바로 찾으세요.",
            gradientColors = listOf(Color(0xFF1A73E8), Color(0xFF7B61FF)),
        ),
        OnboardingPage(
            emoji = "🔎",
            title = "찾기는 더 똑똑하게",
            description = "기본 검색은 무료로 편하게 사용하고,\nMemorix Pro에서는 OCR 검색과 고급 필터로\n사진 속 글자와 오래된 자료까지 찾아냅니다.",
            gradientColors = listOf(Color(0xFF7B61FF), Color(0xFFFF6B9D)),
        ),
        OnboardingPage(
            emoji = "📄",
            title = "공유는 자유롭게 Pro는 더 깔끔하게",
            description = "기본 사진 공유는 무료로 유지합니다.\nPro에서는 여러 장 묶음 공유, PDF 내보내기,\n백업/복구와 프라이빗 보관함을 더 안전하게 사용할 수 있습니다.",
            gradientColors = listOf(Color(0xFFFF6B35), Color(0xFFFFB347)),
        ),
    )
}
