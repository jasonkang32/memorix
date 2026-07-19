package com.mebonsoft.memorix.data.repository

import com.mebonsoft.memorix.core.database.dao.AlbumDao
import com.mebonsoft.memorix.core.database.dao.MediaDao
import com.mebonsoft.memorix.core.database.entity.AlbumEntity
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.feature.albums.AlbumEditorSupport
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DefaultAlbumRepository @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
) : AlbumRepository {
    override fun observeAlbumSummaries(): Flow<List<AlbumSummary>> = albumDao.observeAlbumSummaries()

    override fun observeAlbum(albumId: Long): Flow<AlbumEntity?> = albumDao.observeAlbum(albumId)

    override suspend fun createAlbum(title: String, memo: String): Long = albumDao.insert(
        AlbumEntity(
            title = AlbumEditorSupport.sanitizedTitle(title),
            memo = memo.trim(),
            createdAt = System.currentTimeMillis(),
        )
    )

    override suspend fun updateAlbum(album: AlbumEntity) {
        albumDao.update(album.copy(title = AlbumEditorSupport.sanitizedTitle(album.title), memo = album.memo.trim()))
    }

    override suspend fun deleteAlbum(albumId: Long) {
        mediaDao.clearAlbum(albumId)
        albumDao.delete(albumId)
    }
}
