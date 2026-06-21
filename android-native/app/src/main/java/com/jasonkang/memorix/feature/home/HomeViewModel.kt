package com.jasonkang.memorix.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.media.ImportDateRange
import com.jasonkang.memorix.core.media.ImportPreview
import com.jasonkang.memorix.data.repository.MediaRepository
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
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val transientState = MutableStateFlow(HomeTransientState())

    val uiState: StateFlow<HomeUiState> = combine(
        mediaRepository.observeLibrary(),
        transientState,
    ) { items, transient ->
        HomeUiState(
            items = items,
            isImporting = transient.isImporting,
            errorMessage = transient.errorMessage,
            importSummaryMessage = transient.importSummaryMessage,
            pendingImportPreview = transient.pendingImportPreview,
            selectedImportSpace = transient.selectedImportSpace,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    fun previewImport(uris: List<Uri>, dateRange: ImportDateRange? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    isImporting = true,
                    errorMessage = null,
                    importSummaryMessage = null,
                    pendingImportPreview = null,
                )
            }
            runCatching {
                mediaRepository.previewImport(uris, dateRange)
            }.onFailure { error ->
                transientState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = error.message ?: "가져오기 미리보기에 실패했습니다.",
                        importSummaryMessage = null,
                        pendingImportPreview = null,
                    )
                }
            }.onSuccess { preview ->
                transientState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = null,
                        importSummaryMessage = emptyPreviewMessage(preview),
                        pendingImportPreview = preview.takeIf { result -> result.items.isNotEmpty() },
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val uris = transientState.value.pendingImportPreview?.items?.map { it.uri }.orEmpty()
        if (uris.isEmpty()) {
            transientState.update { it.copy(pendingImportPreview = null) }
            return
        }
        importMedia(uris)
    }

    fun dismissImportPreview() {
        transientState.update { it.copy(pendingImportPreview = null) }
    }

    fun selectImportSpace(space: MediaSpace) {
        transientState.update { it.copy(selectedImportSpace = space) }
    }

    fun importMedia(uris: List<Uri>, space: MediaSpace = transientState.value.selectedImportSpace) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    isImporting = true,
                    errorMessage = null,
                    importSummaryMessage = null,
                )
            }
            runCatching {
                mediaRepository.importMedia(uris, space)
            }.onFailure { error ->
                transientState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = error.message ?: "가져오기에 실패했습니다.",
                        importSummaryMessage = null,
                    )
                }
            }.onSuccess { importedIds ->
                transientState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = null,
                        importSummaryMessage = formatImportSummary(importedIds.size, space),
                        pendingImportPreview = null,
                    )
                }
            }
        }
    }

    fun clearError() {
        transientState.update { it.copy(errorMessage = null) }
    }

    fun clearImportSummary() {
        transientState.update { it.copy(importSummaryMessage = null) }
    }

    private fun formatImportSummary(importedCount: Int, space: MediaSpace): String {
        val spaceLabel = when (space) {
            MediaSpace.WORK -> "Work"
            MediaSpace.PERSONAL -> "Personal"
        }
        return when (importedCount) {
            0 -> "${spaceLabel}에 가져온 항목이 없습니다."
            1 -> "${spaceLabel}에 1개 항목을 가져왔습니다."
            else -> "${spaceLabel}에 ${importedCount}개 항목을 가져왔습니다."
        }
    }

    private fun emptyPreviewMessage(preview: ImportPreview): String? = when {
        preview.items.isNotEmpty() -> null
        preview.dateRange != null -> "선택한 날짜 범위에 가져올 항목이 없습니다."
        else -> "가져올 수 있는 항목이 없습니다."
    }
}

data class HomeUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val importSummaryMessage: String? = null,
    val pendingImportPreview: ImportPreview? = null,
    val selectedImportSpace: MediaSpace = MediaSpace.WORK,
)

private data class HomeTransientState(
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val importSummaryMessage: String? = null,
    val pendingImportPreview: ImportPreview? = null,
    val selectedImportSpace: MediaSpace = MediaSpace.WORK,
)
