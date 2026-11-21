package com.mebonsoft.memorix.core.security

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity

/** 업무·개인·홈 목록에서 잠금·비밀 항목을 어떻게 노출할지. */
object ProtectedListPolicy {
    /**
     * 비밀은 목록에 넣지 않는다. 잠금은 목록에 넣되, 메모·태그·OCR 내용으로 찾아지지 않도록 검색 중에는 뺀다.
     */
    fun isListed(item: MediaItemEntity, query: String, matchesQuery: Boolean): Boolean = when {
        item.isSecret -> false
        item.isLocked -> query.isBlank()
        else -> matchesQuery
    }
}
