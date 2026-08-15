package com.mebonsoft.memorix.feature.personal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.data.repository.AlbumRepository
import com.mebonsoft.memorix.data.repository.MediaRepository
import com.mebonsoft.memorix.feature.search.SearchSupport
import com.mebonsoft.memorix.feature.work.timeline.TimelineSortMode
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
    tagDao: TagDao,
) : ViewModel() {
    private val controls = MutableStateFlow(PersonalControls())
    private val tagAssignments = tagDao.observeMediaTagAssignments()

    val uiState: StateFlow<PersonalUiState> = combine(
        mediaRepository.observeSpace(MediaSpace.PERSONAL),
        albumRepository.observeAlbumSummaries(),
        tagAssignments,
        controls,
    ) { items, albums, assignments, controls ->
        val tagsByMediaId = assignments
            .groupBy { it.mediaId }
            .mapValues { (_, tags) -> tags.map { it.label }.distinct() }
        val filteredItems = items.filter { item ->
            !item.isSecret &&
                SearchSupport.matchesLocal(item, controls.query, tagsByMediaId[item.id].orEmpty()) &&
                (controls.mediaType == null || item.mediaType == controls.mediaType)
        }
        PersonalUiState(
            items = items,
            filteredItems = filteredItems,
            albums = albums,
            tagsByMediaId = tagsByMediaId,
            query = controls.query,
            selectedMediaType = controls.mediaType,
            sortMode = controls.sortMode,
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

    fun updateMediaType(mediaType: MediaType?) {
        controls.update { it.copy(mediaType = mediaType) }
    }

    fun updateSortMode(sortMode: TimelineSortMode) {
        controls.update { it.copy(sortMode = sortMode) }
    }

    fun hideItems(items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            items.distinctBy { it.id }.forEach { item ->
                mediaRepository.updateMedia(item.copy(isSecret = true))
            }
        }
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
                            importMessage = "개인 미디어에 ${importedIds.size}개 항목을 등록했습니다.",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    controls.update {
                        it.copy(
                            isImporting = false,
                            importMessage = null,
                            errorMessage = error.message ?: "개인 미디어 등록에 실패했습니다.",
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
    val tagsByMediaId: Map<Long, List<String>> = emptyMap(),
    val query: String = "",
    val selectedMediaType: MediaType? = null,
    val sortMode: TimelineSortMode = TimelineSortMode.REGISTRATION_TIME,
    val isAlbumGridMode: Boolean = false,
    val summary: String = "앨범 0개 · 미디어 0개",
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)

private data class PersonalControls(
    val query: String = "",
    val mediaType: MediaType? = null,
    val sortMode: TimelineSortMode = TimelineSortMode.REGISTRATION_TIME,
    val isAlbumGridMode: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)
