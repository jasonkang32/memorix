package com.mebonsoft.memorix.data.repository

import com.mebonsoft.memorix.core.database.dao.AlbumDao
import com.mebonsoft.memorix.core.database.dao.MediaDao
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.feature.search.SearchSupport
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DefaultSearchRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val albumDao: AlbumDao,
) : SearchRepository {
    override fun observeAlbumFilters(): Flow<List<AlbumSummary>> = albumDao.observeAlbumSummaries()

    override fun observeResults(
        rawQuery: String,
        space: MediaSpace?,
        albumId: Long?,
        mediaType: MediaType?,
    ): Flow<List<MediaItemEntity>> = mediaDao.observeSearchResults(
        query = SearchSupport.toFtsQuery(rawQuery),
        tagQuery = rawQuery.trim().takeIf { it.isNotBlank() },
        space = space,
        albumId = albumId,
        mediaType = mediaType,
    )
}
