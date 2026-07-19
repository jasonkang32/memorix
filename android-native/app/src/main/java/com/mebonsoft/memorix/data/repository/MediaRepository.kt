package com.mebonsoft.memorix.data.repository

import android.net.Uri
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.media.ImportDateRange
import com.mebonsoft.memorix.core.media.ImportPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface MediaRepository {
    fun observeLibrary(): Flow<List<MediaItemEntity>>
    fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>>
    fun observeAlbum(albumId: Long): Flow<List<MediaItemEntity>>
    fun observeMedia(mediaId: Long): Flow<MediaItemEntity?>
    fun observeTopTags(limit: Int = 10): Flow<List<TagUsageSummary>> = flowOf(emptyList())
    suspend fun previewImport(uris: List<Uri>, dateRange: ImportDateRange? = null): ImportPreview
    suspend fun importMedia(uris: List<Uri>, space: MediaSpace = MediaSpace.WORK): List<Long>
    suspend fun importMediaWithMetadata(
        uris: List<Uri>,
        space: MediaSpace,
        note: String,
        tagIds: List<Long>,
        countryCode: String,
        region: String,
        batchGroupId: String? = null,
    ): List<Long> = importMedia(uris, space)
    suspend fun updateMedia(item: MediaItemEntity)
    suspend fun rebuildSearchIndex(item: MediaItemEntity)
}
