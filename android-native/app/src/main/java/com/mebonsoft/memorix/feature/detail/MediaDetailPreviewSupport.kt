package com.mebonsoft.memorix.feature.detail

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaType

internal fun isFullscreenPreviewAvailable(mediaType: MediaType): Boolean =
    mediaType == MediaType.PHOTO || mediaType == MediaType.VIDEO

internal fun fullscreenPreviewItems(
    selectedItem: MediaItemEntity,
    relatedItems: List<MediaItemEntity>,
): List<MediaItemEntity> {
    if (selectedItem.mediaType != MediaType.PHOTO) return listOf(selectedItem)
    val photos = relatedItems.filter { it.mediaType == MediaType.PHOTO }
    return photos.ifEmpty { listOf(selectedItem) }
}

internal fun fullscreenInitialPage(
    selectedItemId: Long,
    previewItems: List<MediaItemEntity>,
): Int = previewItems.indexOfFirst { it.id == selectedItemId }.takeIf { it >= 0 } ?: 0
