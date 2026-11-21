package com.mebonsoft.memorix.feature.personal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.auth.AuthRepository
import com.mebonsoft.memorix.core.security.ProtectedListPolicy
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PersonalViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
    tagDao: TagDao,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val controls = MutableStateFlow(PersonalControls())
    private val tagAssignments = tagDao.observeMediaTagAssignments()
    private val topPersonalTags = tagDao.observeTopTagsForSpace(MediaSpace.PERSONAL, limit = 8)

    val uiState: StateFlow<PersonalUiState> = combine(
        mediaRepository.observeSpace(MediaSpace.PERSONAL),
        albumRepository.observeAlbumSummaries(),
        tagAssignments,
        topPersonalTags,
        controls,
    ) { items, albums, assignments, topTags, controls ->
        val tagsByMediaId = assignments
            .groupBy { it.mediaId }
            .mapValues { (_, tags) -> tags.map { it.label }.distinct() }
        val filteredItems = items.filter { item ->
            ProtectedListPolicy.isListed(item, controls.query, SearchSupport.matchesLocal(item, controls.query, tagsByMediaId[item.id].orEmpty())) &&
                (controls.selectedTagLabel == null || controls.selectedTagLabel in tagsByMediaId[item.id].orEmpty()) &&
                (controls.mediaType == null || item.mediaType == controls.mediaType)
        }
        PersonalUiState(
            items = items,
            filteredItems = filteredItems,
            albums = albums,
            tagsByMediaId = tagsByMediaId,
            query = controls.query,
            selectedMediaType = controls.mediaType,
            selectedTagLabel = controls.selectedTagLabel,
            topTags = topTags,
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

    fun updateTagFilter(label: String?) {
        controls.update { current ->
            current.copy(selectedTagLabel = label?.takeIf { it != current.selectedTagLabel })
        }
    }

    /** 목록 카드의 자물쇠: 잠금으로 바꾼다. 해제는 상세 화면 보호 설정에서 한다. */
    fun lockItems(items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val hasCredential = authRepository.settings.first().hasPin || authRepository.hasEnrolledBiometric()
            if (!hasCredential) {
                controls.update { it.copy(errorMessage = "잠금을 쓰려면 설정에서 PIN을 먼저 등록하세요.") }
                return@launch
            }
            items.distinctBy { it.id }.forEach { item ->
                mediaRepository.updateMedia(item.copy(isLocked = true))
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
    val selectedTagLabel: String? = null,
    val topTags: List<TagUsageSummary> = emptyList(),
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
    val selectedTagLabel: String? = null,
    val sortMode: TimelineSortMode = TimelineSortMode.REGISTRATION_TIME,
    val isAlbumGridMode: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)
