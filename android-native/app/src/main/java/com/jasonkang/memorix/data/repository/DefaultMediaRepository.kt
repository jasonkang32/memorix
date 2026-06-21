package com.jasonkang.memorix.data.repository

import android.net.Uri
import com.jasonkang.memorix.core.database.dao.MediaDao
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSearchEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.media.DateRangeImportSupport
import com.jasonkang.memorix.core.media.ImportCandidateMetadata
import com.jasonkang.memorix.core.media.ImportDateRange
import com.jasonkang.memorix.core.media.ImportPreview
import com.jasonkang.memorix.core.media.ImportPreviewItem
import com.jasonkang.memorix.core.media.MediaDuplicateDetector
import com.jasonkang.memorix.core.media.MediaDuplicateProbe
import com.jasonkang.memorix.core.media.MediaImportManager
import com.jasonkang.memorix.core.media.MediaImportSupport
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DefaultMediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaImportManager: MediaImportManager,
) : MediaRepository {
    override fun observeLibrary(): Flow<List<MediaItemEntity>> = mediaDao.observeLibrary()

    override fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>> = mediaDao.observeSpace(space)

    override fun observeAlbum(albumId: Long): Flow<List<MediaItemEntity>> = mediaDao.observeAlbum(albumId)

    override fun observeMedia(mediaId: Long): Flow<MediaItemEntity?> = mediaDao.observeById(mediaId)

    override suspend fun previewImport(uris: List<Uri>, dateRange: ImportDateRange?): ImportPreview {
        if (uris.isEmpty()) return ImportPreview(items = emptyList(), dateRange = dateRange)

        val previewed = mediaImportManager.previewAll(
            uris.map { uri -> MediaImportManager.ImportRequest(uri = uri) }
        )
        val metadata = previewed.map { candidate ->
            ImportCandidateMetadata(
                sourceId = candidate.sourceId,
                displayName = candidate.displayName,
                takenAtEpochMillis = candidate.takenAt,
                mediaType = candidate.mediaType,
                fileSizeKb = candidate.fileSizeKb,
            )
        }
        val selectedSourceIds = dateRange
            ?.let { range ->
                DateRangeImportSupport.selectCandidates(metadata, range, ZoneId.systemDefault())
                    .map { it.sourceId }
                    .toSet()
            }
            ?: metadata.map { it.sourceId }.toSet()
        val existingProbes = mediaDao.listLibrary().map { item ->
            MediaDuplicateProbe(
                id = item.id.toString(),
                mediaType = item.mediaType,
                displayName = item.title,
                fileSizeKb = item.fileSizeKb,
                takenAtEpochMillis = item.takenAt,
            )
        }
        val selectedItems = previewed
            .filter { it.sourceId in selectedSourceIds }
            .sortedByDescending { it.takenAt ?: Long.MIN_VALUE }
            .map { candidate ->
                val importedProbe = MediaDuplicateProbe(
                    mediaType = candidate.mediaType,
                    displayName = candidate.displayName,
                    fileSizeKb = candidate.fileSizeKb,
                    takenAtEpochMillis = candidate.takenAt,
                )
                ImportPreviewItem(
                    uri = candidate.uri,
                    sourceId = candidate.sourceId,
                    displayName = candidate.displayName,
                    mediaType = candidate.mediaType,
                    takenAtEpochMillis = candidate.takenAt,
                    fileSizeKb = candidate.fileSizeKb,
                    duplicateMatches = MediaDuplicateDetector.rankCandidates(importedProbe, existingProbes)
                        .filter { it.confidence.priority > 0 }
                        .take(3),
                )
            }

        return ImportPreview(
            items = selectedItems,
            filteredOutCount = previewed.size - selectedItems.size,
            dateRange = dateRange,
        )
    }

    override suspend fun importMedia(uris: List<Uri>, space: MediaSpace): List<Long> {
        if (uris.isEmpty()) return emptyList()
        val imported = mediaImportManager.importAll(
            uris.map { uri -> MediaImportManager.ImportRequest(uri = uri) }
        )
        return imported.map { result ->
            val now = System.currentTimeMillis()
            val item = MediaItemEntity(
                space = space,
                mediaType = result.mediaType,
                filePath = result.originalFile.absolutePath,
                thumbPath = result.thumbnailFile?.absolutePath,
                title = MediaImportSupport.inferTitle(result.displayName),
                note = "",
                takenAt = result.takenAt ?: now,
                createdAt = now,
                fileSizeKb = result.fileSizeKb,
                durationSec = result.durationSec,
                mimeType = result.mimeType,
                width = result.width,
                height = result.height,
            )
            val id = mediaDao.insert(item)
            rebuildSearchIndex(item.copy(id = id))
            id
        }
    }

    override suspend fun updateMedia(item: MediaItemEntity) {
        mediaDao.update(item)
        rebuildSearchIndex(item)
    }

    override suspend fun rebuildSearchIndex(item: MediaItemEntity) {
        mediaDao.upsertSearchIndex(
            MediaSearchEntity(
                rowId = item.id,
                title = item.title,
                note = item.note,
                ocrText = item.ocrText,
            )
        )
    }
}
