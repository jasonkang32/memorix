package com.mebonsoft.memorix.core.security

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity

/** 기록 보호 단계. 잠금과 비밀은 둘 다 파일을 암호화하고, 목록 노출 여부만 다르다. */
enum class MediaProtection(val label: String, val description: String) {
    None("일반", "목록에 그대로 보입니다."),
    Locked("잠금", "목록에는 보이지만 미리보기를 가리고, 열 때 생체인증 또는 PIN을 요구합니다."),
    Secret("비밀", "목록에서 숨기고 설정 > 비밀 보관함에서만 볼 수 있습니다."),
    ;

    companion object {
        fun of(item: MediaItemEntity): MediaProtection = when {
            item.isSecret -> Secret
            item.isLocked -> Locked
            else -> None
        }
    }
}

fun MediaItemEntity.withProtection(protection: MediaProtection): MediaItemEntity = copy(
    isLocked = protection == MediaProtection.Locked,
    isSecret = protection == MediaProtection.Secret,
)
