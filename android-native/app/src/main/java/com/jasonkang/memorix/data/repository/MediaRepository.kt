package com.jasonkang.memorix.data.repository

import android.net.Uri
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.media.ImportDateRange
import com.jasonkang.memorix.core.media.ImportPreview
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeLibrary(): Flow<List<MediaItemEntity>>
    fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>>
    fun observeAlbum(albumId: Long): Flow<List<MediaItemEntity>>
    fun observeMedia(mediaId: Long): Flow<MediaItemEntity?>
    suspend fun previewImport(uris: List<Uri>, dateRange: ImportDateRange? = null): ImportPreview
    suspend fun importMedia(uris: List<Uri>, space: MediaSpace = MediaSpace.WORK): List<Long>
    suspend fun updateMedia(item: MediaItemEntity)
    suspend fun rebuildSearchIndex(item: MediaItemEntity)
}
