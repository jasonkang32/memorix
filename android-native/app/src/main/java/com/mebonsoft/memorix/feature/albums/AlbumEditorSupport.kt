package com.mebonsoft.memorix.feature.albums

object AlbumEditorSupport {
    fun sanitizedTitle(raw: String): String = raw.trim().ifBlank { "새 앨범" }
}
