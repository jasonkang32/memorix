package com.mebonsoft.memorix.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.database.entity.AlbumEntity
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.data.repository.AlbumRepository
import com.mebonsoft.memorix.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val albumId: Long = checkNotNull(savedStateHandle["albumId"])

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        albumRepository.observeAlbum(albumId),
        mediaRepository.observeAlbum(albumId),
    ) { album, items ->
        AlbumDetailUiState(album = album, items = items)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailUiState(),
    )

    fun updateAlbum(title: String, memo: String) {
        val current = uiState.value.album ?: return
        viewModelScope.launch {
            albumRepository.updateAlbum(current.copy(title = title, memo = memo))
        }
    }

    fun deleteAlbum() {
        viewModelScope.launch { albumRepository.deleteAlbum(albumId) }
    }
}

data class AlbumDetailUiState(
    val album: AlbumEntity? = null,
    val items: List<MediaItemEntity> = emptyList(),
)
