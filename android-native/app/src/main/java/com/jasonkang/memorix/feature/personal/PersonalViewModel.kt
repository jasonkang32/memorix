package com.jasonkang.memorix.feature.personal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasonkang.memorix.core.database.entity.AlbumSummary
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.data.repository.AlbumRepository
import com.jasonkang.memorix.data.repository.MediaRepository
import com.jasonkang.memorix.feature.search.SearchSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PersonalViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    private val controls = MutableStateFlow(PersonalControls())

    val uiState: StateFlow<PersonalUiState> = combine(
        mediaRepository.observeSpace(MediaSpace.PERSONAL),
        albumRepository.observeAlbumSummaries(),
        controls,
    ) { items, albums, controls ->
        val filteredItems = items.filter { item -> SearchSupport.matchesLocal(item, controls.query) }
        PersonalUiState(
            items = items,
            filteredItems = filteredItems,
            albums = albums,
            query = controls.query,
            isAlbumGridMode = controls.isAlbumGridMode,
            summary = "앨범 ${albums.size}개 · 미디어 ${items.size}개",
            isImporting = controls.isImporting,
            importMessage = controls.importMessage,
            errorMessage = controls.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PersonalUiState(),
    )

    fun updateQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun toggleAlbumGrid() {
        controls.update { it.copy(isAlbumGridMode = !it.isAlbumGridMode) }
    }

    fun createAlbum(title: String, memo: String) {
        viewModelScope.launch {
            albumRepository.createAlbum(title.trim(), memo.trim())
        }
    }

    fun importMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            controls.update { it.copy(isImporting = true, importMessage = null, errorMessage = null) }
            runCatching { mediaRepository.importMedia(uris, MediaSpace.PERSONAL) }
                .onSuccess { importedIds ->
                    controls.update {
                        it.copy(
                            isImporting = false,
                            importMessage = "Personal에 ${importedIds.size}개 항목을 등록했습니다.",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    controls.update {
                        it.copy(
                            isImporting = false,
                            importMessage = null,
                            errorMessage = error.message ?: "Personal 등록에 실패했습니다.",
                        )
                    }
                }
        }
    }

    fun consumeImportMessages() {
        controls.update { it.copy(importMessage = null, errorMessage = null) }
    }
}

data class PersonalUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val filteredItems: List<MediaItemEntity> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val query: String = "",
    val isAlbumGridMode: Boolean = false,
    val summary: String = "앨범 0개 · 미디어 0개",
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)

private data class PersonalControls(
    val query: String = "",
    val isAlbumGridMode: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)
