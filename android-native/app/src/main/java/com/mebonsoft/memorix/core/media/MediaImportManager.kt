package com.mebonsoft.memorix.core.media

import android.content.Context
import android.net.Uri
import com.mebonsoft.memorix.core.database.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MediaImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataReader: MediaMetadataReader,
) {
    data class ImportRequest(
        val uri: Uri,
        val mediaType: MediaType? = null,
    )

    data class ImportResult(
        val originalFile: File,
        val thumbnailFile: File?,
        val mediaType: MediaType,
        val mimeType: String,
        val takenAt: Long?,
        val fileSizeKb: Long,
        val durationSec: Long,
        val width: Int?,
        val height: Int?,
        val displayName: String?,
    )

    data class PreviewResult(
        val uri: Uri,
        val sourceId: String,
        val mediaType: MediaType,
        val mimeType: String,
        val takenAt: Long?,
        val fileSizeKb: Long,
        val durationSec: Long,
        val width: Int?,
        val height: Int?,
        val displayName: String,
    )

    suspend fun previewAll(requests: List<ImportRequest>): List<PreviewResult> = withContext(Dispatchers.IO) {
        requests.map { preview(it) }
    }

    suspend fun preview(request: ImportRequest): PreviewResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val source = metadataReader.describeSource(resolver, request.uri)
        val mediaType = request.mediaType ?: MediaImportSupport.inferMediaType(
            mimeType = source.mimeType,
            displayName = source.displayName,
        )
        val metadata = metadataReader.readSourceMetadata(
            resolver = resolver,
            uri = request.uri,
            mediaType = mediaType,
            fallbackSizeBytes = source.sizeBytes,
            sourceFileTimeMillis = source.sourceFileTimeMillis,
        )

        PreviewResult(
            uri = request.uri,
            sourceId = request.uri.toString(),
            mediaType = mediaType,
            mimeType = source.mimeType.orEmpty(),
            takenAt = metadata.takenAt,
            fileSizeKb = metadata.fileSizeKb,
            durationSec = metadata.durationSec,
            width = metadata.width,
            height = metadata.height,
            displayName = source.displayName ?: request.uri.lastPathSegment ?: "가져온 항목",
        )
    }

    suspend fun importAll(requests: List<ImportRequest>): List<ImportResult> = withContext(Dispatchers.IO) {
        requests.map { import(it) }
    }

    suspend fun import(request: ImportRequest): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val source = metadataReader.describeSource(resolver, request.uri)
        val mediaType = request.mediaType ?: MediaImportSupport.inferMediaType(
            mimeType = source.mimeType,
            displayName = source.displayName,
        )
        val extension = MediaImportSupport.inferExtension(
            mimeType = source.mimeType,
            displayName = source.displayName,
            mediaType = mediaType,
        )

        val root = File(context.filesDir, "memorix")
        val uuid = UUID.randomUUID().toString()
        val output = StorageLayout.originalFile(root, extension, uuid)
        val thumb = StorageLayout.thumbFile(root, uuid)
        output.parentFile?.mkdirs()

        try {
            resolver.openInputStream(request.uri).use { input ->
                requireNotNull(input) { "Cannot open input stream for ${request.uri}" }
                FileOutputStream(output).use { stream -> input.copyTo(stream) }
            }

            val mimeType = source.mimeType.orEmpty()
            val metadata = metadataReader.readMetadata(
                file = output,
                mediaType = mediaType,
                mimeType = mimeType,
                sourceFileTimeMillis = source.sourceFileTimeMillis,
            )
            val thumbnailFile = if (mediaType == MediaType.VIDEO) {
                metadataReader.createVideoThumbnail(output, thumb)
            } else {
                null
            }

            ImportResult(
                originalFile = output,
                thumbnailFile = thumbnailFile,
                mediaType = mediaType,
                mimeType = mimeType,
                takenAt = metadata.takenAt,
                fileSizeKb = metadata.fileSizeKb,
                durationSec = metadata.durationSec,
                width = metadata.width,
                height = metadata.height,
                displayName = source.displayName,
            )
        } catch (error: Exception) {
            output.delete()
            thumb.delete()
            throw error
        }
    }
}
