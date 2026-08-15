package com.mebonsoft.memorix.core.media

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.mebonsoft.memorix.core.database.entity.MediaType
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
        val sourceFileTimeMillis: Long?,
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
                sourceFileTimeMillis = resolver.querySourceFileTimeMillis(uri),
            )
        }
    }

    fun readSourceMetadata(
        resolver: ContentResolver,
        uri: Uri,
        mediaType: MediaType,
        fallbackSizeBytes: Long? = null,
        sourceFileTimeMillis: Long? = null,
    ): Metadata = when (mediaType) {
        MediaType.PHOTO -> readPhotoMetadataFromUri(resolver, uri, fallbackSizeBytes, sourceFileTimeMillis)
        MediaType.VIDEO -> readVideoMetadataFromUri(uri, fallbackSizeBytes, sourceFileTimeMillis)
        MediaType.DOCUMENT -> readDocumentMetadataFromUri(fallbackSizeBytes, sourceFileTimeMillis)
    }

    fun readMetadata(
        file: File,
        mediaType: MediaType,
        mimeType: String,
        sourceFileTimeMillis: Long? = null,
    ): Metadata = when (mediaType) {
        MediaType.PHOTO -> readPhotoMetadata(file, sourceFileTimeMillis)
        MediaType.VIDEO -> readVideoMetadata(file, sourceFileTimeMillis)
        MediaType.DOCUMENT -> readDocumentMetadata(file, sourceFileTimeMillis)
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

    fun createPhotoThumbnail(photoFile: File, outputFile: File, maxLongEdge: Int = 512): File? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoFile.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sampleSize = Integer.highestOneBit((longest / maxLongEdge).coerceAtLeast(1))
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, options) ?: return@runCatching null
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)) return@runCatching null
        }
        bitmap.recycle()
        outputFile
    }.getOrNull()

    private fun readPhotoMetadata(file: File, sourceFileTimeMillis: Long?): Metadata {
        val exif = ExifInterface(file)
        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
        return Metadata(
            takenAt = resolveSourceTakenAt(
                embeddedTakenAt = exif.dateTimeOriginalToEpochMillis(),
                sourceFileTime = sourceFileTimeMillis ?: file.lastModified().takeIf { it > 0L },
                nowMillis = System.currentTimeMillis(),
            ),
            fileSizeKb = file.length() / 1024,
            width = width,
            height = height,
        )
    }

    private fun readVideoMetadata(file: File, sourceFileTimeMillis: Long?): Metadata {
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
            Metadata(
                takenAt = resolveSourceTakenAt(
                    embeddedTakenAt = dateTaken,
                    sourceFileTime = sourceFileTimeMillis ?: file.lastModified().takeIf { it > 0L },
                    nowMillis = System.currentTimeMillis(),
                ),
                fileSizeKb = file.length() / 1024,
                durationSec = durationMs / 1000,
                width = width,
                height = height,
            )
        } finally {
            retriever.release()
        }
    }

    private fun readDocumentMetadata(file: File, sourceFileTimeMillis: Long?): Metadata = Metadata(
        takenAt = sourceFileTimeMillis ?: file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
        fileSizeKb = file.length() / 1024,
    )

    private fun readPhotoMetadataFromUri(
        resolver: ContentResolver,
        uri: Uri,
        fallbackSizeBytes: Long?,
        sourceFileTimeMillis: Long?,
    ): Metadata = runCatching {
        val nowMillis = System.currentTimeMillis()
        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
            Metadata(
                takenAt = resolveSourceTakenAt(exif.dateTimeOriginalToEpochMillis(), sourceFileTimeMillis, nowMillis),
                fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024,
                width = width,
                height = height,
            )
        }
    }.getOrNull() ?: Metadata(
        takenAt = resolveSourceTakenAt(null, sourceFileTimeMillis, System.currentTimeMillis()),
        fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024,
    )

    private fun readVideoMetadataFromUri(
        uri: Uri,
        fallbackSizeBytes: Long?,
        sourceFileTimeMillis: Long?,
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
                takenAt = resolveSourceTakenAt(dateTaken, sourceFileTimeMillis, System.currentTimeMillis()),
                fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024,
                durationSec = durationMs / 1000,
                width = width,
                height = height,
            )
        } catch (_: Exception) {
            Metadata(
                takenAt = resolveSourceTakenAt(null, sourceFileTimeMillis, System.currentTimeMillis()),
                fileSizeKb = (fallbackSizeBytes ?: 0L) / 1024,
            )
        } finally {
            retriever.release()
        }
    }

    private fun readDocumentMetadataFromUri(
        fallbackSizeBytes: Long?,
        sourceFileTimeMillis: Long?,
    ): Metadata = Metadata(
        takenAt = sourceFileTimeMillis,
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
            sourceFileTimeMillis = null,
        )
    }

    private fun ContentResolver.querySourceFileTimeMillis(uri: Uri): Long? = runCatching {
        query(uri, null, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return@runCatching null
            cursor.getMillisColumn(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                ?: cursor.getSecondsColumn(MediaStore.MediaColumns.DATE_MODIFIED)
                ?: cursor.getSecondsColumn(MediaStore.MediaColumns.DATE_ADDED)
                ?: cursor.getMillisColumn(MediaStore.Images.Media.DATE_TAKEN)
        }
    }.getOrNull()

    private fun Cursor.getMillisColumn(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getLong(index).takeIf { it > 0L }
    }

    private fun Cursor.getSecondsColumn(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return mediaStoreSecondsToMillis(getLong(index))
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

internal fun resolveSourceTakenAt(
    embeddedTakenAt: Long?,
    sourceFileTime: Long?,
    nowMillis: Long,
): Long = sourceFileTime ?: embeddedTakenAt ?: nowMillis

internal fun mediaStoreSecondsToMillis(seconds: Long?): Long? = seconds
    ?.takeIf { it > 0L }
    ?.times(1000L)
