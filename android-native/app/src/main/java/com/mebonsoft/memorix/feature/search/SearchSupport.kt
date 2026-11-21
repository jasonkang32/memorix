package com.mebonsoft.memorix.feature.search

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaType
import java.util.Locale

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

    fun matchesLocal(item: MediaItemEntity, rawQuery: String, tags: List<String> = emptyList()): Boolean {
        val tokens = rawQuery.trim().split(Regex("\\s+"))
            .map { it.removePrefix("#").lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val haystack = (listOf(item.title, item.note, item.ocrText,
            item.countryCode, item.region, item.sourceDisplayName) + tags)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return tokens.all { haystack.contains(it) }
    }
}
