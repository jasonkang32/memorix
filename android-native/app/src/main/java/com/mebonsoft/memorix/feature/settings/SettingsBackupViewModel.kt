package com.mebonsoft.memorix.feature.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.mebonsoft.memorix.BuildConfig
import com.mebonsoft.memorix.core.backup.BackupExportMode
import com.mebonsoft.memorix.core.backup.BackupProgress
import com.mebonsoft.memorix.core.backup.ManagedStorageUsage
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
import com.mebonsoft.memorix.core.cloud.CloudAutoBackupSettings
import com.mebonsoft.memorix.core.cloud.CloudSyncOperations
import com.mebonsoft.memorix.core.cloud.CloudSyncPreferences
import com.mebonsoft.memorix.core.cloud.DriveAutoBackupScheduler
import com.mebonsoft.memorix.core.cloud.DriveCloudSyncStatus
import com.mebonsoft.memorix.core.cloud.DriveCloudSyncSupport
import com.mebonsoft.memorix.core.cloud.DriveConnectOutcome
import com.mebonsoft.memorix.core.cloud.DriveSyncException
import com.mebonsoft.memorix.core.media.OriginalCleanupSummary
import com.mebonsoft.memorix.core.media.OriginalMediaCleanupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DecimalFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: MemorixBackupOperations,
    private val cloudSync: CloudSyncOperations,
    private val cloudPreferences: CloudSyncPreferences,
    private val autoBackupScheduler: DriveAutoBackupScheduler,
    private val originalCleanupManager: OriginalMediaCleanupManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsBackupUiState(cloudDiagnostics = buildDiagnostics()),
    )
    val uiState: StateFlow<SettingsBackupUiState> = _uiState

    private fun buildDiagnostics(): CloudDiagnostics {
        val status = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(-1)
        return CloudDiagnostics(
            playServicesLabel = when (status) {
                ConnectionResult.SUCCESS -> "Play 서비스 정상"
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Play 서비스 업데이트 필요"
                ConnectionResult.SERVICE_MISSING -> "Play 서비스 없음"
                ConnectionResult.SERVICE_INVALID -> "Play 서비스 비정상"
                ConnectionResult.SERVICE_DISABLED -> "Play 서비스 사용 중지됨"
                else -> "Play 서비스 상태 확인 불가($status)"
            },
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    init {
        refreshManagedStorageUsage()
        refreshOriginalCleanupSummary()
        refreshCloudStatus()
        viewModelScope.launch {
            cloudPreferences.autoBackup.collect { settings ->
                _uiState.update { it.copy(autoBackup = settings) }
            }
        }
        viewModelScope.launch {
            cloudPreferences.connectError.collect { error ->
                _uiState.update { it.copy(cloudLastError = error) }
            }
        }
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

    fun selectBackupMode(mode: BackupExportMode) {
        _uiState.update { it.copy(selectedBackupMode = mode) }
    }

    /** Google Drive 연결. 이미 승인되어 있으면 즉시 연결되고, 아니면 동의 화면 IntentSender를 상태에 담는다. */
    fun connectDrive() {
        viewModelScope.launch {
            cloudPreferences.clearConnectError()
            _uiState.update {
                it.copy(
                    cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = true, connectionError = null),
                    connectStage = DriveConnectStage.RequestingAuthorization,
                    errorMessage = null,
                    infoMessage = null,
                    pendingConsentIntentSender = null,
                )
            }
            runCatching { cloudSync.requestAuthorization() }
                .onSuccess { outcome ->
                    when (outcome) {
                        is DriveConnectOutcome.Connected -> onDriveConnected(outcome.status)
                        is DriveConnectOutcome.ConsentRequired -> _uiState.update {
                            it.copy(
                                cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = false),
                                connectStage = DriveConnectStage.WaitingForConsent,
                                pendingConsentIntentSender = outcome.intentSender,
                            )
                        }
                    }
                }
                .onFailure { error -> onDriveFailure(error) }
        }
    }

    fun consumeConsentIntentSender() {
        _uiState.update { it.copy(pendingConsentIntentSender = null) }
    }

    /**
     * 승인 화면에서 돌아왔을 때 결과 Intent가 유실된 경우(프로세스 재시작 등)를 복구한다.
     * 이미 승인이 끝났으면 조용히 연결되고, 아직이면 대기 상태를 유지한다(동의 화면을 반복 실행하지 않는다).
     */
    fun recheckAuthorizationAfterReturn() {
        if (_uiState.value.connectStage != DriveConnectStage.WaitingForConsent) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectStage = DriveConnectStage.CompletingAuthorization,
                    cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = true),
                )
            }
            runCatching { cloudSync.requestAuthorization() }
                .onSuccess { outcome ->
                    when (outcome) {
                        is DriveConnectOutcome.Connected -> onDriveConnected(outcome.status)
                        is DriveConnectOutcome.ConsentRequired -> _uiState.update {
                            it.copy(
                                cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = false),
                                connectStage = DriveConnectStage.WaitingForConsent,
                            )
                        }
                    }
                }
                .onFailure { error -> onDriveFailure(error) }
        }
    }

    fun onDriveConsentResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            if (data == null) {
                val reason = if (resultCode == Activity.RESULT_CANCELED) {
                    "Google 승인 화면이 결과 없이 닫혔습니다(취소 또는 OAuth 설정 오류). " +
                        "동의 화면의 drive.appdata scope, 테스트 사용자/게시 상태, APK 서명 SHA-1 등록을 확인하세요."
                } else {
                    "Google 승인 결과를 받지 못했습니다(결과 코드 $resultCode). 잠시 후 다시 시도하세요."
                }
                onDriveFailure(DriveSyncException(reason))
                return@launch
            }
            _uiState.update {
                it.copy(
                    cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = true),
                    connectStage = DriveConnectStage.CompletingAuthorization,
                    errorMessage = null,
                    infoMessage = null,
                )
            }
            runCatching { cloudSync.completeAuthorization(data) }
                .onSuccess { status -> onDriveConnected(status) }
                .onFailure { error -> onDriveFailure(error) }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !_uiState.value.cloudSyncStatus.isConnected) {
                _uiState.update { it.copy(errorMessage = "자동 백업을 켜려면 먼저 Google Drive를 연결하세요.") }
                return@launch
            }
            cloudPreferences.updateAutoBackup(enabled = enabled)
            val settings = cloudPreferences.currentAutoBackup()
            autoBackupScheduler.sync(settings)
            _uiState.update {
                it.copy(
                    errorMessage = null,
                    infoMessage = if (enabled) {
                        "자동 백업을 켰습니다. ${DriveCloudSyncSupport.autoBackupSummary(settings)}"
                    } else {
                        "자동 백업을 껐습니다."
                    },
                )
            }
        }
    }

    fun setAutoBackupInterval(hours: Int) {
        viewModelScope.launch {
            cloudPreferences.updateAutoBackup(intervalHours = hours)
            val settings = cloudPreferences.currentAutoBackup()
            autoBackupScheduler.sync(settings)
            _uiState.update { it.copy(infoMessage = "자동 백업 주기를 ${DriveCloudSyncSupport.intervalLabel(hours)}(으)로 변경했습니다.") }
        }
    }

    fun setAutoBackupWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            cloudPreferences.updateAutoBackup(wifiOnly = wifiOnly)
            autoBackupScheduler.sync(cloudPreferences.currentAutoBackup())
            _uiState.update {
                it.copy(infoMessage = if (wifiOnly) "Wi-Fi에서만 자동 백업합니다." else "모바일 데이터에서도 자동 백업합니다.")
            }
        }
    }

    fun setAutoBackupMode(mode: BackupExportMode) {
        viewModelScope.launch {
            cloudPreferences.updateAutoBackup(mode = mode)
            _uiState.update {
                it.copy(infoMessage = "자동 백업 범위를 ${backupModeLabel(mode)} 백업으로 설정했습니다.")
            }
        }
    }

    private fun onDriveConnected(status: DriveCloudSyncStatus) {
        _uiState.update {
            it.copy(
                cloudSyncStatus = status.copy(isWorking = false, connectionError = null),
                connectStage = DriveConnectStage.Idle,
                infoMessage = "Google Drive 연결을 완료했습니다.",
            )
        }
        viewModelScope.launch {
            cloudPreferences.clearConnectError()
            val settings = cloudPreferences.currentAutoBackup()
            if (settings.enabled) autoBackupScheduler.sync(settings)
        }
    }

    private fun onDriveFailure(error: Throwable) {
        val message = error.message ?: "Google Drive 연결에 실패했습니다."
        Log.w(TAG, "drive connect failed: $message", error)
        _uiState.update {
            it.copy(
                cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = false),
                connectStage = DriveConnectStage.Idle,
                errorMessage = message,
            )
        }
        viewModelScope.launch { cloudPreferences.recordConnectError(message) }
    }

    fun refreshCloudStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = true), errorMessage = null, infoMessage = null) }
            runCatching { cloudSync.refreshStatus() }
                .onSuccess { status ->
                    _uiState.update { it.copy(cloudSyncStatus = status.copy(isWorking = false)) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = it.cloudSyncStatus.copy(isWorking = false),
                            errorMessage = error.message ?: "Google Drive 연결 상태를 확인하지 못했습니다.",
                        )
                    }
                }
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
                    val restoredQuick = status.latestBackup?.mode == BackupExportMode.Quick
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = status.copy(isWorking = false),
                            infoMessage = if (restoredQuick) {
                                "Drive에 전체 백업이 없어 빠른 백업으로 복구했습니다. 원본 파일은 포함되지 않습니다. 앱을 완전히 종료 후 다시 열어주세요."
                            } else {
                                "Google Drive 전체 백업을 복구했습니다. 앱을 완전히 종료 후 다시 열어주세요."
                            },
                        )
                    }
                }
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            runCatching { cloudSync.disconnect() }
                .onSuccess {
                    cloudPreferences.updateAutoBackup(enabled = false)
                    autoBackupScheduler.cancel()
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = DriveCloudSyncStatus(),
                            pendingConsentIntentSender = null,
                            infoMessage = "Google Drive 연결을 해제했습니다. 자동 백업도 함께 꺼졌습니다.",
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
    val autoBackup: CloudAutoBackupSettings = CloudAutoBackupSettings(),
    val connectStage: DriveConnectStage = DriveConnectStage.Idle,
    val cloudLastError: String? = null,
    val cloudDiagnostics: CloudDiagnostics = CloudDiagnostics(),
    val pendingConsentIntentSender: IntentSender? = null,
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

/** Google Drive 연결 진행 단계. 화면에 그대로 표시해 어디서 멈췄는지 보이게 한다. */
enum class DriveConnectStage(val label: String) {
    Idle(""),
    RequestingAuthorization("Google 승인 요청 중..."),
    WaitingForConsent("Google 승인 화면 대기 중..."),
    CompletingAuthorization("승인 결과 확인 중..."),
}

/** 연결이 안 될 때 원인 후보를 좁히기 위한 기기 진단 정보. */
data class CloudDiagnostics(
    val playServicesLabel: String = "확인 중",
    val appVersion: String = "",
) {
    val summary: String
        get() = listOf(playServicesLabel, "Memorix $appVersion")
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}

private const val TAG = "MemorixDrive"

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
