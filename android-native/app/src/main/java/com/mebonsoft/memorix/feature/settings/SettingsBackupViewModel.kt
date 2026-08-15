package com.mebonsoft.memorix.feature.settings

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.backup.BackupExportMode
import com.mebonsoft.memorix.core.backup.BackupProgress
import com.mebonsoft.memorix.core.backup.ManagedStorageUsage
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
import com.mebonsoft.memorix.core.cloud.CloudSyncOperations
import com.mebonsoft.memorix.core.cloud.DriveCloudSyncStatus
import com.mebonsoft.memorix.core.media.OriginalCleanupSummary
import com.mebonsoft.memorix.core.media.OriginalMediaCleanupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DecimalFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    private val backupManager: MemorixBackupOperations,
    private val cloudSync: CloudSyncOperations,
    private val originalCleanupManager: OriginalMediaCleanupManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsBackupUiState())
    val uiState: StateFlow<SettingsBackupUiState> = _uiState

    init {
        refreshManagedStorageUsage()
        refreshOriginalCleanupSummary()
        refreshCloudStatus()
    }

    fun refreshManagedStorageUsage() {
        viewModelScope.launch {
            runCatching { backupManager.calculateManagedStorageUsage() }
                .onSuccess { usage -> _uiState.update { it.copy(managedStorageUsage = usage) } }
        }
    }

    fun refreshOriginalCleanupSummary() {
        viewModelScope.launch {
            runCatching { originalCleanupManager.calculateSummary() }
                .onSuccess { summary -> _uiState.update { it.copy(originalCleanupSummary = summary) } }
        }
    }

    fun prepareOriginalCleanup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null, pendingOriginalCleanupIntent = null) }
            runCatching { originalCleanupManager.prepareDeleteRequest() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            errorMessage = error.message ?: "원본 파일 정리를 준비하지 못했습니다.",
                        )
                    }
                }
                .onSuccess { request ->
                    if (request.pendingIntent == null) {
                        val message = if (request.itemIds.isNotEmpty()) {
                            "원본 파일 정리를 완료했습니다. ${request.itemIds.size}개 항목을 정리했습니다."
                        } else {
                            "정리 가능한 원본 파일이 없습니다."
                        }
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                originalCleanupSummary = request.summary,
                                infoMessage = message,
                            )
                        }
                        refreshOriginalCleanupSummary()
                    } else {
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                pendingOriginalCleanupIntent = request.pendingIntent,
                                pendingOriginalCleanupIds = request.itemIds,
                                originalCleanupSummary = request.summary,
                            )
                        }
                    }
                }
        }
    }

    fun consumePendingOriginalCleanupIntent() {
        _uiState.update { it.copy(pendingOriginalCleanupIntent = null) }
    }

    fun onOriginalCleanupResult(confirmed: Boolean) {
        val ids = _uiState.value.pendingOriginalCleanupIds
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null, pendingOriginalCleanupIntent = null) }
            if (confirmed) {
                originalCleanupManager.markDeleteRequestCompleted(ids)
            } else {
                originalCleanupManager.markDeleteRequestCancelled(ids)
            }
            val summary = originalCleanupManager.calculateSummary()
            _uiState.update {
                it.copy(
                    isWorking = false,
                    pendingOriginalCleanupIds = emptyList(),
                    originalCleanupSummary = summary,
                    infoMessage = if (confirmed) "원본 파일 정리가 완료되었습니다." else "원본 파일 정리를 취소했습니다.",
                )
            }
            refreshManagedStorageUsage()
        }
    }

    fun createDriveSignInIntent(): Intent = cloudSync.createSignInIntent()

    fun selectBackupMode(mode: BackupExportMode) {
        _uiState.update { it.copy(selectedBackupMode = mode) }
    }

    fun onDriveSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = true), errorMessage = null, infoMessage = null) }
            runCatching { cloudSync.handleSignInResult(data) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = false),
                            errorMessage = error.message ?: "Google Drive 연결에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = status.copy(isWorking = false),
                            infoMessage = "Google Drive를 연결했습니다.",
                        )
                    }
                }
        }
    }

    fun refreshCloudStatus() {
        viewModelScope.launch {
            runCatching { cloudSync.refreshStatus() }
                .onSuccess { status -> _uiState.update { it.copy(cloudSyncStatus = status) } }
        }
    }

    fun uploadCloudBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = true), errorMessage = null, infoMessage = null) }
            runCatching { cloudSync.uploadCloudBackup() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = false),
                            errorMessage = error.message ?: "Google Drive 백업에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = status.copy(isWorking = false),
                            infoMessage = "Google Drive 백업을 완료했습니다.",
                        )
                    }
                }
        }
    }

    fun restoreLatestCloudBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = true), errorMessage = null, infoMessage = null) }
            runCatching { cloudSync.restoreLatestCloudBackup() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = false),
                            errorMessage = error.message ?: "Google Drive 복구에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = status.copy(isWorking = false),
                            infoMessage = "Google Drive 최신 백업을 복구했습니다. 앱을 완전히 종료 후 다시 열어주세요.",
                        )
                    }
                }
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            runCatching { cloudSync.disconnect() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = DriveCloudSyncStatus(),
                            infoMessage = "Google Drive 연결을 해제했습니다.",
                        )
                    }
                }
        }
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            val mode = _uiState.value.selectedBackupMode
            _uiState.update {
                it.copy(
                    isWorking = true,
                    errorMessage = null,
                    infoMessage = null,
                    backupProgress = BackupProgress(0, 0),
                )
            }
            runCatching {
                backupManager.exportBackup(
                    destination = destination,
                    mode = mode,
                    onProgress = { progress -> _uiState.update { it.copy(backupProgress = progress) } },
                )
            }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            backupProgress = null,
                            errorMessage = error.message ?: "백업에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { usage ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            managedStorageUsage = usage,
                            backupProgress = null,
                            infoMessage = "${backupModeLabel(mode)} 백업을 완료했습니다. 총 ${formatBytes(usage.totalBytes)} 보관함을 저장했습니다.",
                        )
                    }
                }
        }
    }

    fun restoreBackup(source: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null) }
            runCatching { backupManager.restoreBackup(source) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            errorMessage = error.message ?: "복구에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { usage ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            managedStorageUsage = usage,
                            infoMessage = "복구를 완료했습니다. DB 재연결을 위해 앱을 완전히 종료 후 다시 열어주세요.",
                        )
                    }
                }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null) }
            runCatching { backupManager.resetAllData() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            errorMessage = error.message ?: "초기화에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { usage ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            managedStorageUsage = usage,
                            infoMessage = "초기화를 완료했습니다. Home과 보관함 정보가 바로 비워졌습니다.",
                        )
                    }
                }
        }
    }

    fun consumeMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}

data class SettingsBackupUiState(
    val managedStorageUsage: ManagedStorageUsage = ManagedStorageUsage(mediaBytes = 0L, databaseBytes = 0L),
    val originalCleanupSummary: OriginalCleanupSummary = OriginalCleanupSummary(),
    val cloudSyncStatus: DriveCloudSyncStatus = DriveCloudSyncStatus(),
    val selectedBackupMode: BackupExportMode = BackupExportMode.Full,
    val backupProgress: BackupProgress? = null,
    val isWorking: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val pendingOriginalCleanupIntent: PendingIntent? = null,
    val pendingOriginalCleanupIds: List<Long> = emptyList(),
)

internal fun backupModeLabel(mode: BackupExportMode): String = when (mode) {
    BackupExportMode.Full -> "전체"
    BackupExportMode.Quick -> "빠른"
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return if (index == 0) "${bytes} B" else "${DecimalFormat("#,##0.#").format(value)} ${units[index]}"
}
