package com.mebonsoft.memorix.feature.detail

import com.mebonsoft.memorix.core.database.entity.MediaType

internal fun isFullscreenPreviewAvailable(mediaType: MediaType): Boolean =
    mediaType == MediaType.PHOTO || mediaType == MediaType.VIDEO
