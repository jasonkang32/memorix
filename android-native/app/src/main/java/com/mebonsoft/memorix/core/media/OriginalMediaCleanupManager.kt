package com.mebonsoft.memorix.core.media

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mebonsoft.memorix.core.database.dao.MediaDao
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OriginalCleanupDeleteRequest(
    val pendingIntent: PendingIntent?,
    val itemIds: List<Long>,
    val summary: OriginalCleanupSummary,
)

@Singleton
class OriginalMediaCleanupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
) {
    suspend fun calculateSummary(): OriginalCleanupSummary = withContext(Dispatchers.IO) {
        OriginalMediaCleanupSupport.summarize(mediaDao.listOriginalCleanupSources().map { it.toOriginalSourceCandidate() })
    }

    suspend fun prepareDeleteRequest(): OriginalCleanupDeleteRequest = withContext(Dispatchers.IO) {
        val candidates = mediaDao.listOriginalCleanupSources().map { it.toOriginalSourceCandidate() }
        val summary = OriginalMediaCleanupSupport.summarize(candidates)
        if (summary.cleanableIds.isEmpty()) {
            return@withContext OriginalCleanupDeleteRequest(pendingIntent = null, itemIds = emptyList(), summary = summary)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val deletedIds = deleteDirectly(summary.cleanableIds)
            return@withContext OriginalCleanupDeleteRequest(
                pendingIntent = null,
                itemIds = deletedIds,
                summary = summary.copy(cleanableIds = deletedIds, cleanableCount = deletedIds.size),
            )
        }

        val items = mediaDao.listByIds(summary.cleanableIds)
        val uris = items
            .filter { OriginalMediaCleanupSupport.isMediaStoreVisualSource(it.sourceUri, it.mediaType) }
            .mapNotNull { runCatching { Uri.parse(it.sourceUri) }.getOrNull() }

        if (uris.isEmpty()) {
            mediaDao.updateOriginalCleanupStatus(
                ids = summary.cleanableIds,
                status = OriginalSourceCleanupStatus.UNAVAILABLE,
                deletedAt = null,
                error = "삭제 가능한 MediaStore 원본을 찾지 못했습니다.",
            )
            return@withContext OriginalCleanupDeleteRequest(pendingIntent = null, itemIds = emptyList(), summary = OriginalCleanupSummary())
        }

        OriginalCleanupDeleteRequest(
            pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris),
            itemIds = summary.cleanableIds,
            summary = summary,
        )
    }

    suspend fun markDeleteRequestCompleted(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            mediaDao.updateOriginalCleanupStatus(
                ids = ids,
                status = OriginalSourceCleanupStatus.DELETED,
                deletedAt = System.currentTimeMillis(),
                error = "",
            )
        }
    }

    suspend fun markDeleteRequestCancelled(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            mediaDao.updateOriginalCleanupStatus(
                ids = ids,
                status = OriginalSourceCleanupStatus.AVAILABLE,
                deletedAt = null,
                error = "사용자가 원본 삭제를 취소했습니다.",
            )
        }
    }

    private suspend fun deleteDirectly(ids: List<Long>): List<Long> {
        val deleted = mutableListOf<Long>()
        mediaDao.listByIds(ids).forEach { item ->
            val rows = runCatching { context.contentResolver.delete(Uri.parse(item.sourceUri), null, null) }.getOrDefault(0)
            if (rows > 0) deleted += item.id
        }
        if (deleted.isNotEmpty()) {
            mediaDao.updateOriginalCleanupStatus(deleted, OriginalSourceCleanupStatus.DELETED, System.currentTimeMillis(), "")
        }
        val failed = ids - deleted.toSet()
        if (failed.isNotEmpty()) {
            mediaDao.updateOriginalCleanupStatus(failed, OriginalSourceCleanupStatus.FAILED, null, "원본 삭제 권한이 없거나 이미 삭제되었습니다.")
        }
        return deleted
    }
}

private fun MediaItemEntity.toOriginalSourceCandidate(): OriginalSourceCandidate = OriginalSourceCandidate(
    id = id,
    mediaType = mediaType,
    sourceUri = sourceUri,
    sourceSizeKb = sourceSizeKb,
    cleanupStatus = sourceCleanupStatus,
    space = space,
)
