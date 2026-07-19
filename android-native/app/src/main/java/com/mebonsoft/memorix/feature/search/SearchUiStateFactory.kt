package com.mebonsoft.memorix.feature.search

object SearchUiStateFactory {
    fun createSummary(
        query: String,
        resultCount: Int,
        albumTitle: String?,
        mediaTypeLabel: String,
    ): String {
        val chips = buildList {
            if (query.isNotBlank()) add("'${query.trim()}'")
            if (!albumTitle.isNullOrBlank()) add(albumTitle)
            if (mediaTypeLabel != "전체") add(mediaTypeLabel)
        }
        val prefix = if (chips.isEmpty()) "전체 미디어" else chips.joinToString(" · ")
        return "$prefix · ${resultCount}건"
    }
}
