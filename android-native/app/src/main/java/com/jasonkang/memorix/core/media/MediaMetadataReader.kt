package com.jasonkang.memorix.core.media

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.jasonkang.memorix.core.database.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SourceDescriptor(
        val displayName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
    )

    data class Metadata(
        val takenAt: Long? = null,
        val fileSizeKb: Long = 0,
        val durationSec: Long = 0,
        val width: Int? = null,
        val height: Int? = null,
    )

    fun describeSource(
        resolver: ContentResolver,
        uri: Uri,
    ): SourceDescriptor {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null).use { cursor ->
            val descriptor = cursor?.readSourceDescriptor()
            return SourceDescriptor(
                displayName = descriptor?.displayName,
                mimeType = resolver.getType(uri),
                sizeBytes = descriptor?.sizeBytes,
            )
        }
    }

    fun readSourceMetadata(
        resolver: ContentResolver,
        uri: Uri,
        mediaType: MediaType,
        fallbackSizeBytes: Long? = null,
    ): Metadata = when (mediaType) {
        MediaType.PHOTO -> readPhotoMetadataFromUri(resolver, uri, fallbackSizeBytes)
        MediaType.VIDEO -> readVideoMetadataFromUri(uri, fallbackSizeBytes)
        MediaType.DOCUMENT -> readDocumentMetadataFromUri(fallbackSizeBytes)
    }

    fun readMetadata(
        file: File,
        mediaType: MediaType,
        mimeType: String,
    ): Metadata = when (mediaType) {
        MediaType.PHOTO -> readPhotoMetadata(file)
        MediaType.VIDEO -> readVideoMetadata(file)
        MediaType.DOCUMENT -> readDocumentMetadata(file)
    }

    fun createVideoThumbnail(videoFile: File, outputFile: File): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(0)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { stream ->
                val compressed = frame?.compress(Bitmap.CompressFormat.JPEG, 80, stream) ?: false
                if (!compressed) return null
            }
            outputFile
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun readPhotoMetadata(file: File): Metadata {
        val exif = ExifInterface(file)
        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
        return Metadata(
            takenAt = exif.dateTimeOriginalToEpochMillis() ?: file.lastModified(),
            fileSizeKb = file.length() / 1024,
            width = width,
            height = height,
        )
    }

    private fun readVideoMetadata(file: File): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val dateTaken = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?.let(::parseRetrieverDate)
                ?: file.lastModified()
            Metadata(
                takenAt = dateTaken,
                fileSizeKb = file.length() / 1024,
                durationSec = durationMs / 1000,
                width = width,
                height = height,
            )
        } finally {
            retriever.release()
        }
    }

    private fun readDocumentMetadata(file: File): Metadata = Metadata(
        takenAt = file.lastModified(),
        fileSizeKb = file.length() / 1024,
    )

    private fun readPhotoMetadataFromUri(
        resolver: ContentResolver,
        uri: Uri,
        fallbackSizeBytes: Long?,
    ): Metadata = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
            Metadata(
                takenAt = exif.dateTimeOriginalToEpochMillis(),
                fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024,
                width = width,
                height = height,
            )
        }
    }.getOrNull() ?: Metadata(fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024)

    private fun readVideoMetadataFromUri(
        uri: Uri,
        fallbackSizeBytes: Long?,
    ): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val dateTaken = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?.let(::parseRetrieverDate)
            Metadata(
                takenAt = dateTaken,
                fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024,
                durationSec = durationMs / 1000,
                width = width,
                height = height,
            )
        } catch (_: Exception) {
            Metadata(fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024)
        } finally {
            retriever.release()
        }
    }

    private fun readDocumentMetadataFromUri(fallbackSizeBytes: Long?): Metadata = Metadata(
        fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024,
    )

    private fun Cursor.readSourceDescriptor(): SourceDescriptor? {
        if (!moveToFirst()) return null
        val nameIndex = getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = getColumnIndex(OpenableColumns.SIZE)
        return SourceDescriptor(
            displayName = if (nameIndex >= 0) getString(nameIndex) else null,
            mimeType = null,
            sizeBytes = if (sizeIndex >= 0 && !isNull(sizeIndex)) getLong(sizeIndex) else null,
        )
    }

    private fun ExifInterface.dateTimeOriginalToEpochMillis(): Long? {
        val raw = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: getAttribute(ExifInterface.TAG_DATETIME)
        return raw?.let {
            runCatching {
                val normalized = it.replaceFirst(':', '-').replaceFirst(':', '-')
                Instant.parse(normalized.replace(' ', 'T') + "Z").toEpochMilli()
            }.getOrNull()
        }
    }

    private fun parseRetrieverDate(raw: String): Long? = runCatching {
        Instant.parse(raw).toEpochMilli()
    }.getOrNull()
}
