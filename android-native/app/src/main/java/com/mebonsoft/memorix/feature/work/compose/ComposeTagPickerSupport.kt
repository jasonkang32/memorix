package com.mebonsoft.memorix.feature.work.compose

import com.mebonsoft.memorix.core.database.entity.TagEntity

internal const val ComposeTagPreviewLimit = 10

data class ComposeTagPreview(
    val selectedTags: List<TagEntity>,
    val suggestedTags: List<TagEntity>,
    val hiddenCount: Int,
)

internal fun buildComposeTagPreview(
    tags: List<TagEntity>,
    selectedTagIds: List<Long>,
    limit: Int = ComposeTagPreviewLimit,
): ComposeTagPreview {
    val selectedIdSet = selectedTagIds.toSet()
    val selectedTags = selectedTagIds.mapNotNull { selectedId -> tags.firstOrNull { it.id == selectedId } }
    val suggestedTags = tags
        .asSequence()
        .filter { it.id !in selectedIdSet }
        .sortedWith(compareBy<TagEntity> { it.label.lowercase() }.thenBy { it.id })
        .take(limit)
        .toList()
    val hiddenCount = (tags.size - selectedTags.size - suggestedTags.size).coerceAtLeast(0)
    return ComposeTagPreview(
        selectedTags = selectedTags,
        suggestedTags = suggestedTags,
        hiddenCount = hiddenCount,
    )
}

internal fun filterComposeTags(
    tags: List<TagEntity>,
    query: String,
    selectedTagIds: List<Long>,
): List<TagEntity> {
    val normalizedQuery = query.trim().trimStart('#').lowercase()
    val selectedIdSet = selectedTagIds.toSet()
    return tags
        .asSequence()
        .filter { tag ->
            normalizedQuery.isBlank() ||
                tag.label.lowercase().contains(normalizedQuery) ||
                tag.key.lowercase().contains(normalizedQuery)
        }
        .sortedWith(
            compareByDescending<TagEntity> { it.id in selectedIdSet }
                .thenBy { it.label.lowercase() }
                .thenBy { it.id },
        )
        .toList()
}

internal fun hasExactTagMatch(tags: List<TagEntity>, query: String): Boolean {
    val normalizedQuery = query.trim().trimStart('#')
    if (normalizedQuery.isBlank()) return true
    return tags.any { tag ->
        tag.label.equals(normalizedQuery, ignoreCase = true) ||
            tag.key.equals(normalizedQuery.lowercase(), ignoreCase = true)
    }
}
