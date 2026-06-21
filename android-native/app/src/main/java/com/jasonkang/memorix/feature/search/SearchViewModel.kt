package com.jasonkang.memorix.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasonkang.memorix.core.database.entity.AlbumSummary
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaType
import com.jasonkang.memorix.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(SearchFilters())

    private val albumsFlow = searchRepository.observeAlbumFilters()

    private val resultsFlow = filters.flatMapLatest { current ->
        searchRepository.observeResults(
            rawQuery = current.query,
            albumId = current.albumId,
            mediaType = current.mediaType,
        )
    }

    val uiState: StateFlow<SearchUiState> = combine(
        filters,
        albumsFlow,
        resultsFlow,
    ) { currentFilters, albums, results ->
        SearchUiState(
            query = currentFilters.query,
            selectedAlbumId = currentFilters.albumId,
            selectedMediaType = currentFilters.mediaType,
            albums = albums,
            results = results,
            summary = SearchUiStateFactory.createSummary(
                query = currentFilters.query,
                resultCount = results.size,
                albumTitle = albums.firstOrNull { it.id == currentFilters.albumId }?.title,
                mediaTypeLabel = SearchSupport.mediaTypeLabel(currentFilters.mediaType),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun updateQuery(value: String) {
        filters.update { it.copy(query = value) }
    }

    fun updateAlbum(albumId: Long?) {
        filters.update { it.copy(albumId = albumId) }
    }

    fun updateMediaType(mediaType: MediaType?) {
        filters.update { it.copy(mediaType = mediaType) }
    }
}

data class SearchUiState(
    val query: String = "",
    val selectedAlbumId: Long? = null,
    val selectedMediaType: MediaType? = null,
    val albums: List<AlbumSummary> = emptyList(),
    val results: List<MediaItemEntity> = emptyList(),
    val summary: String = "전체 미디어 · 0건",
)

private data class SearchFilters(
    val query: String = "",
    val albumId: Long? = null,
    val mediaType: MediaType? = null,
)
