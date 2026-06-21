package com.jasonkang.memorix.core.media

import com.jasonkang.memorix.core.database.entity.MediaType
import java.util.Locale

object MediaImportSupport {
    fun inferMediaType(mimeType: String?, displayName: String?): MediaType {
        val normalizedMime = mimeType?.lowercase(Locale.US).orEmpty()
        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            .orEmpty()

        return when {
            normalizedMime.startsWith("image/") -> MediaType.PHOTO
            normalizedMime.startsWith("video/") -> MediaType.VIDEO
            extension in imageExtensions -> MediaType.PHOTO
            extension in videoExtensions -> MediaType.VIDEO
            else -> MediaType.DOCUMENT
        }
    }

    fun inferExtension(
        mimeType: String?,
        displayName: String?,
        mediaType: MediaType,
    ): String {
        val fromName = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()
        if (fromName.isNotEmpty()) return fromName

        val fromMime = mimeTypeToExtension[mimeType?.lowercase(Locale.US)]
        if (!fromMime.isNullOrBlank()) return fromMime

        return defaultExtension(mediaType)
    }

    fun inferTitle(displayName: String?): String {
        val normalized = displayName
            ?.substringBeforeLast('.', missingDelimiterValue = displayName)
            ?.trim()
            .orEmpty()

        return normalized.ifBlank { "가져온 항목" }
    }

    fun defaultExtension(mediaType: MediaType): String = when (mediaType) {
        MediaType.PHOTO -> "jpg"
        MediaType.VIDEO -> "mp4"
        MediaType.DOCUMENT -> "bin"
    }

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic")
    private val videoExtensions = setOf("mp4", "mov", "m4v", "webm", "avi", "mkv")

    private val mimeTypeToExtension = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/heic" to "heic",
        "video/mp4" to "mp4",
        "video/quicktime" to "mov",
        "video/webm" to "webm",
        "application/pdf" to "pdf",
    )
}
