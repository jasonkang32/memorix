package com.jasonkang.memorix.feature.search

import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaType

object SearchSupport {
    fun toFtsQuery(raw: String): String? {
        val tokens = raw.trim().split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { token -> "${token}*" }
    }

    fun mediaTypeLabel(type: MediaType?): String = when (type) {
        null -> "전체"
        MediaType.PHOTO -> "사진"
        MediaType.VIDEO -> "영상"
        MediaType.DOCUMENT -> "문서"
    }

    fun matchesLocal(item: MediaItemEntity, rawQuery: String): Boolean {
        val query = rawQuery.trim().lowercase()
        if (query.isBlank()) return true
        val haystack = listOf(item.title, item.note, item.ocrText)
            .joinToString(" ")
            .lowercase()
        return haystack.contains(query)
    }
}
