package com.mebonsoft.memorix.feature.work.compose

import com.mebonsoft.memorix.core.database.entity.MediaSpace

internal data class ComposeSpaceProfile(
    val title: String,
    val noteLabel: String,
    val notePlaceholder: String,
    val tagLabel: String,
    val locationLabel: String,
    val locationHint: String,
)

internal fun composeSpaceProfile(space: MediaSpace): ComposeSpaceProfile = when (space) {
    MediaSpace.WORK -> ComposeSpaceProfile(
        title = "Work 등록",
        noteLabel = "업무 메모",
        notePlaceholder = "필요한 설명만 적고, 분류는 아래 태그로 관리하세요...",
        tagLabel = "태그",
        locationLabel = "업무 위치",
        locationHint = "현장/업체 위치가 있으면 남겨두세요. 첫 번째 사진 EXIF 위치도 자동 반영됩니다.",
    )
    MediaSpace.PERSONAL -> ComposeSpaceProfile(
        title = "Personal 등록",
        noteLabel = "개인 메모",
        notePlaceholder = "오늘의 기억을 조용히 남기고, 분류는 아래 태그로 관리하세요...",
        tagLabel = "태그",
        locationLabel = "기억 장소",
        locationHint = "장소가 있는 기억은 나중에 더 쉽게 떠올릴 수 있습니다. 사진 EXIF 위치도 자동 반영됩니다.",
    )
}
