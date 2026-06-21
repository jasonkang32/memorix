package com.jasonkang.memorix.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasonkang.memorix.core.database.entity.AlbumSummary
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.data.repository.AlbumRepository
import com.jasonkang.memorix.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"])

    val uiState: StateFlow<MediaDetailUiState> = combine(
        mediaRepository.observeMedia(mediaId),
        albumRepository.observeAlbumSummaries(),
    ) { item, albums ->
        MediaDetailUiState(item = item, albums = albums)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaDetailUiState(),
    )

    fun save(item: MediaItemEntity) {
        viewModelScope.launch { mediaRepository.updateMedia(item) }
    }
}

data class MediaDetailUiState(
    val item: MediaItemEntity? = null,
    val albums: List<AlbumSummary> = emptyList(),
)
