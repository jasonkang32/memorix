package com.mebonsoft.memorix.feature.work

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.auth.AuthRepository
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
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
class WorkViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository,
    tagDao: TagDao,
) : ViewModel() {
    private val controls = MutableStateFlow(WorkControls())
    private val tagAssignments = tagDao.observeMediaTagAssignments()
    private val topWorkTags = tagDao.observeTopTagsForSpace(MediaSpace.WORK, limit = 8)

    init {
        viewModelScope.launch {
            authRepository.settings.collect { settings ->
                controls.update {
                    it.copy(
                        hasAuthCredential = settings.hasPin || settings.biometricEnabled,
                        canUseBiometric = settings.biometricEnabled && authRepository.canUseBiometric(),
                    )
                }
            }
        }
    }

    val uiState: StateFlow<WorkUiState> = combine(
        mediaRepository.observeSpace(MediaSpace.WORK),
        tagAssignments,
        topWorkTags,
        controls,
    ) { items, assignments, topTags, controls ->
        val tagsByMediaId = assignments
            .groupBy { it.mediaId }
            .mapValues { (_, tags) -> tags.map { it.label }.distinct() }
        val filteredItems = items.filter { item ->
            val itemTags = tagsByMediaId[item.id].orEmpty()
            val visibleBySecret = controls.showSecrets || !item.isSecret
            visibleBySecret &&
                SearchSupport.matchesLocal(item, controls.query, itemTags) &&
                (controls.selectedTagLabel == null || controls.selectedTagLabel in itemTags) &&
                (controls.mediaType == null || item.mediaType == controls.mediaType)
        }
        WorkUiState(
            items = items,
            filteredItems = filteredItems,
            tagsByMediaId = tagsByMediaId,
            query = controls.query,
            selectedMediaType = controls.mediaType,
            selectedTagLabel = controls.selectedTagLabel,
            sortMode = controls.sortMode,
            topTags = topTags,
            secretUnlocked = controls.showSecrets,
            hasAuthCredential = controls.hasAuthCredential,
            canUseBiometric = controls.canUseBiometric,
            summary = buildSummary(filteredItems, controls.mediaType),
            isImporting = controls.isImporting,
            importMessage = controls.importMessage,
            errorMessage = controls.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkUiState(),
    )

    fun updateQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun updateMediaType(mediaType: MediaType?) {
        controls.update { it.copy(mediaType = mediaType) }
    }

    fun updateTagFilter(label: String?) {
        controls.update { current ->
            current.copy(selectedTagLabel = label?.takeIf { it != current.selectedTagLabel })
        }
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

    fun lockSecrets() {
        controls.update { it.copy(showSecrets = false, errorMessage = null) }
    }

    fun unlockSecretsByBiometric() {
        controls.update { it.copy(showSecrets = true, errorMessage = null) }
    }

    fun unlockSecretsByPin(pin: String) {
        viewModelScope.launch {
            val ok = authRepository.verifyPin(pin)
            controls.update {
                it.copy(
                    showSecrets = ok,
                    errorMessage = if (ok) null else "PIN이 올바르지 않습니다.",
                )
            }
        }
    }

    fun importMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            controls.update { it.copy(isImporting = true, importMessage = null, errorMessage = null) }
            runCatching { mediaRepository.importMedia(uris, MediaSpace.WORK) }
                .onSuccess { importedIds ->
                    controls.update {
                        it.copy(
                            isImporting = false,
                            importMessage = "Work에 ${importedIds.size}개 항목을 등록했습니다.",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    controls.update {
                        it.copy(
                            isImporting = false,
                            importMessage = null,
                            errorMessage = error.message ?: "Work 등록에 실패했습니다.",
                        )
                    }
                }
        }
    }

    fun consumeImportMessages() {
        controls.update { it.copy(importMessage = null, errorMessage = null) }
    }
}

data class WorkUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val filteredItems: List<MediaItemEntity> = emptyList(),
    val tagsByMediaId: Map<Long, List<String>> = emptyMap(),
    val query: String = "",
    val selectedMediaType: MediaType? = null,
    val selectedTagLabel: String? = null,
    val sortMode: TimelineSortMode = TimelineSortMode.REGISTRATION_TIME,
    val topTags: List<TagUsageSummary> = emptyList(),
    val secretUnlocked: Boolean = false,
    val hasAuthCredential: Boolean = false,
    val canUseBiometric: Boolean = false,
    val summary: String = "전체 0개",
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)

private data class WorkControls(
    val query: String = "",
    val mediaType: MediaType? = null,
    val selectedTagLabel: String? = null,
    val sortMode: TimelineSortMode = TimelineSortMode.REGISTRATION_TIME,
    val showSecrets: Boolean = false,
    val hasAuthCredential: Boolean = false,
    val canUseBiometric: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)

private fun buildSummary(items: List<MediaItemEntity>, selectedMediaType: MediaType?): String {
    val count = items.size
    val prefix = SearchSupport.mediaTypeLabel(selectedMediaType)
    return if (selectedMediaType == null) {
        "전체 ${count}개"
    } else {
        "$prefix ${count}개"
    }
}
