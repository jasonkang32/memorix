package com.mebonsoft.memorix.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
import com.mebonsoft.memorix.core.backup.ManagedStorageUsage
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.media.ImportDateRange
import com.mebonsoft.memorix.core.media.ImportPreview
import com.mebonsoft.memorix.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val backupManager: MemorixBackupOperations,
) : ViewModel() {
    private val transientState = MutableStateFlow(HomeTransientState())
    private var importJob: Job? = null

    init {
        refreshManagedStorageUsage()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        mediaRepository.observeLibrary(),
        mediaRepository.observeTopTags(limit = 10),
        transientState,
    ) { items, topTags, transient ->
        val visibleItems = items.filterNot { it.isSecret }
        HomeUiState(
            items = visibleItems,
            topTags = topTags,
            isImporting = transient.isImporting,
            errorMessage = transient.errorMessage,
            importSummaryMessage = transient.importSummaryMessage,
            pendingImportPreview = transient.pendingImportPreview,
            selectedImportSpace = transient.selectedImportSpace,
            managedStorageUsage = transient.managedStorageUsage,
            isBackupWorking = transient.isBackupWorking,
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
        importJob?.cancel()
        importJob = viewModelScope.launch {
            transientState.update {
                it.copy(
                    isImporting = true,
                    errorMessage = null,
                    importSummaryMessage = null,
                )
            }
            try {
                val importedIds = mediaRepository.importMedia(uris, space)
                refreshManagedStorageUsage()
                transientState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = null,
                        importSummaryMessage = formatImportSummary(importedIds.size, space),
                        pendingImportPreview = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                transientState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = null,
                        importSummaryMessage = null,
                        pendingImportPreview = null,
                    )
                }
                throw cancelled
            } catch (error: Throwable) {
                transientState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = error.message ?: "가져오기에 실패했습니다.",
                        importSummaryMessage = null,
                    )
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        transientState.update {
            it.copy(
                isImporting = false,
                errorMessage = null,
                importSummaryMessage = null,
                pendingImportPreview = null,
            )
        }
    }

    fun clearError() {
        transientState.update { it.copy(errorMessage = null) }
    }

    fun clearImportSummary() {
        transientState.update { it.copy(importSummaryMessage = null) }
    }

    fun showError(message: String) {
        transientState.update {
            it.copy(
                isImporting = false,
                errorMessage = message,
                importSummaryMessage = null,
            )
        }
    }

    fun refreshManagedStorageUsage() {
        viewModelScope.launch {
            runCatching { backupManager.calculateManagedStorageUsage() }
                .onSuccess { usage -> transientState.update { it.copy(managedStorageUsage = usage) } }
        }
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            transientState.update { it.copy(isBackupWorking = true, errorMessage = null, importSummaryMessage = null) }
            runCatching { backupManager.exportBackup(destination) }
                .onFailure { error ->
                    transientState.update {
                        it.copy(
                            isBackupWorking = false,
                            errorMessage = error.message ?: "백업에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { usage ->
                    transientState.update {
                        it.copy(
                            isBackupWorking = false,
                            managedStorageUsage = usage,
                            importSummaryMessage = "백업을 완료했습니다. 총 ${formatBytes(usage.totalBytes)} 보관함을 저장했습니다.",
                        )
                    }
                }
        }
    }

    fun restoreBackup(source: Uri) {
        viewModelScope.launch {
            transientState.update { it.copy(isBackupWorking = true, errorMessage = null, importSummaryMessage = null) }
            runCatching { backupManager.restoreBackup(source) }
                .onFailure { error ->
                    transientState.update {
                        it.copy(
                            isBackupWorking = false,
                            errorMessage = error.message ?: "복구에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { usage ->
                    transientState.update {
                        it.copy(
                            isBackupWorking = false,
                            managedStorageUsage = usage,
                            importSummaryMessage = "복구를 완료했습니다. DB 재연결을 위해 앱을 완전히 종료 후 다시 열어주세요.",
                        )
                    }
                }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index += 1
        }
        return if (index == 0) "${bytes} B" else "%.1f %s".format(value, units[index])
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
        preview.dateRange != null && preview.dateRange.dayCount == 1L -> "선택한 날짜에 가져올 항목이 없습니다."
        preview.dateRange != null -> "선택한 날짜 범위에 가져올 항목이 없습니다."
        else -> "가져올 수 있는 항목이 없습니다."
    }
}

data class HomeUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val topTags: List<TagUsageSummary> = emptyList(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val importSummaryMessage: String? = null,
    val pendingImportPreview: ImportPreview? = null,
    val selectedImportSpace: MediaSpace = MediaSpace.WORK,
    val managedStorageUsage: ManagedStorageUsage = ManagedStorageUsage(mediaBytes = 0L, databaseBytes = 0L),
    val isBackupWorking: Boolean = false,
)

private data class HomeTransientState(
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val importSummaryMessage: String? = null,
    val pendingImportPreview: ImportPreview? = null,
    val selectedImportSpace: MediaSpace = MediaSpace.WORK,
    val managedStorageUsage: ManagedStorageUsage = ManagedStorageUsage(mediaBytes = 0L, databaseBytes = 0L),
    val isBackupWorking: Boolean = false,
)
