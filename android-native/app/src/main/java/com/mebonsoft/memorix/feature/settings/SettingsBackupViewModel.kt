package com.mebonsoft.memorix.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.backup.ManagedStorageUsage
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsBackupUiState())
    val uiState: StateFlow<SettingsBackupUiState> = _uiState

    init {
        refreshManagedStorageUsage()
    }

    fun refreshManagedStorageUsage() {
        viewModelScope.launch {
            runCatching { backupManager.calculateManagedStorageUsage() }
                .onSuccess { usage -> _uiState.update { it.copy(managedStorageUsage = usage) } }
        }
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null) }
            runCatching { backupManager.exportBackup(destination) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            errorMessage = error.message ?: "백업에 실패했습니다.",
                        )
                    }
                }
                .onSuccess { usage ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            managedStorageUsage = usage,
                            infoMessage = "백업을 완료했습니다. 총 ${formatBytes(usage.totalBytes)} 보관함을 저장했습니다.",
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
    val isWorking: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

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
