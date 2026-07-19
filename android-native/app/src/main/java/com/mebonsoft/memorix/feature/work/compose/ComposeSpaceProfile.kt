package com.mebonsoft.memorix.feature.work.compose

import com.mebonsoft.memorix.core.database.entity.MediaSpace

internal data class ComposeSpaceProfile(
    val title: String,
    val noteLabel: String,
    val notePlaceholder: String,
    val tagLabel: String,
    val locationLabel: String,
    val locationHint: String,
    val quickSections: List<ComposeQuickSection>,
)

internal data class ComposeQuickSection(
    val title: String,
    val description: String,
    val labels: List<String>,
)

internal fun composeSpaceProfile(space: MediaSpace): ComposeSpaceProfile = when (space) {
    MediaSpace.WORK -> ComposeSpaceProfile(
        title = "Work 등록",
        noteLabel = "업무 메모",
        notePlaceholder = "업무 관련 메모를 입력하세요...",
        tagLabel = "업무 태그",
        locationLabel = "업무 위치",
        locationHint = "현장/업체 위치가 있으면 남겨두세요. 첫 번째 사진 EXIF 위치도 자동 반영됩니다.",
        quickSections = listOf(
            ComposeQuickSection(
                title = "기록 종류",
                description = "업무를 관리하지 않고, 나중에 찾기 쉽게 성격만 남깁니다.",
                labels = listOf("회의", "요청", "결정", "아이디어", "자료"),
            ),
            ComposeQuickSection(
                title = "업무 맥락",
                description = "어떤 일과 관련된 기록인지 가볍게 표시합니다.",
                labels = listOf("내부", "거래처", "현장", "비용", "계약"),
            ),
            ComposeQuickSection(
                title = "보관 이유",
                description = "왜 남겼는지만 기록해 검색과 회상에 집중합니다.",
                labels = listOf("참고용", "공유용", "확인용", "증빙용"),
            ),
        ),
    )
    MediaSpace.PERSONAL -> ComposeSpaceProfile(
        title = "Personal 등록",
        noteLabel = "개인 메모",
        notePlaceholder = "오늘의 기억을 조용히 남겨보세요...",
        tagLabel = "기억 태그",
        locationLabel = "기억 장소",
        locationHint = "장소가 있는 기억은 나중에 더 쉽게 떠올릴 수 있습니다. 사진 EXIF 위치도 자동 반영됩니다.",
        quickSections = listOf(
            ComposeQuickSection(
                title = "감정",
                description = "그때의 기분을 함께 보관합니다.",
                labels = listOf("좋음", "평온", "감사", "행복", "아쉬움", "피곤"),
            ),
            ComposeQuickSection(
                title = "함께한 사람",
                description = "사람 중심으로 다시 찾을 수 있게 남깁니다.",
                labels = listOf("가족", "친구", "동료", "혼자"),
            ),
            ComposeQuickSection(
                title = "기억 종류",
                description = "개인 기억의 성격을 부드럽게 분류합니다.",
                labels = listOf("여행", "음식", "건강", "일상", "생각", "아이디어"),
            ),
        ),
    )
}
