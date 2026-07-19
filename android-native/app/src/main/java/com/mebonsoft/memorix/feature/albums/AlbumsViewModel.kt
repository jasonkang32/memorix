package com.mebonsoft.memorix.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.database.entity.AlbumEntity
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.data.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    val uiState: StateFlow<AlbumsUiState> = albumRepository.observeAlbumSummaries()
        .map { summaries -> AlbumsUiState(albums = summaries) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlbumsUiState(),
        )

    fun createAlbum(title: String, memo: String) {
        viewModelScope.launch { albumRepository.createAlbum(title, memo) }
    }

    fun updateAlbum(album: AlbumEntity) {
        viewModelScope.launch { albumRepository.updateAlbum(album) }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch { albumRepository.deleteAlbum(albumId) }
    }
}

data class AlbumsUiState(
    val albums: List<AlbumSummary> = emptyList(),
)
