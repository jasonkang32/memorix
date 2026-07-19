package com.mebonsoft.memorix.data.repository

import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    fun observeAlbumFilters(): Flow<List<AlbumSummary>>
    fun observeResults(
        rawQuery: String,
        space: MediaSpace? = null,
        albumId: Long?,
        mediaType: MediaType?,
    ): Flow<List<MediaItemEntity>>
}
