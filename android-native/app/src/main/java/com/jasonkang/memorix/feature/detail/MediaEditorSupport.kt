package com.jasonkang.memorix.feature.detail

import com.jasonkang.memorix.core.database.entity.MediaItemEntity

object MediaEditorSupport {
    fun applyDraft(
        item: MediaItemEntity,
        title: String,
        note: String,
        albumId: Long?,
        takenAt: Long,
        isFavorite: Boolean,
    ): MediaItemEntity = item.copy(
        title = title.trim(),
        note = note.trim(),
        albumId = albumId,
        takenAt = takenAt,
        isFavorite = isFavorite,
    )
}
