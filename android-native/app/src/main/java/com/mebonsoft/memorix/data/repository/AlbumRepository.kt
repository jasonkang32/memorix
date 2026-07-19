package com.mebonsoft.memorix.data.repository

import com.mebonsoft.memorix.core.database.entity.AlbumEntity
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun observeAlbumSummaries(): Flow<List<AlbumSummary>>
    fun observeAlbum(albumId: Long): Flow<AlbumEntity?>
    suspend fun createAlbum(title: String, memo: String): Long
    suspend fun updateAlbum(album: AlbumEntity)
    suspend fun deleteAlbum(albumId: Long)
}
