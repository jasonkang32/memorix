package com.mebonsoft.memorix.feature.detail

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace

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

    fun relatedWorkItems(
        selected: MediaItemEntity,
        candidates: List<MediaItemEntity>,
    ): List<MediaItemEntity> {
        val selectedKey = detailGroupKey(selected)
        return candidates
            .filter { candidate ->
                candidate.space == selected.space &&
                    !candidate.isTrashed &&
                    detailGroupKey(candidate) == selectedKey
            }
            .sortedWith(compareBy<MediaItemEntity> { it.createdAt }.thenBy { it.id })
    }

    fun workDeleteTargets(
        selected: MediaItemEntity,
        relatedItems: List<MediaItemEntity>,
    ): List<MediaItemEntity> = if (selected.space == MediaSpace.WORK) {
        (relatedItems.ifEmpty { listOf(selected) })
            .filter { !it.isTrashed }
            .distinctBy { it.id }
    } else {
        listOf(selected)
    }

    private fun detailGroupKey(item: MediaItemEntity): String =
        item.batchGroupId.ifBlank { "${item.takenAt / 3_600_000}_${item.note.hashCode()}_${item.region}" }
}
