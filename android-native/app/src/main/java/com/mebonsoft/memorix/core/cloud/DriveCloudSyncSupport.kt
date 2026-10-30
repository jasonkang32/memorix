package com.mebonsoft.memorix.core.cloud

import com.mebonsoft.memorix.core.backup.BackupExportMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CLOUD_BACKUP_PREFIX = "Memorix_Cloud_"
private const val CLOUD_BACKUP_EXTENSION = ".zip"

/** 빠른 백업(originals 제외) 파일명 접미사. 접미사가 없으면 전체 백업이다. */
private const val QUICK_BACKUP_SUFFIX = "_quick"
const val MEMORIX_CLOUD_BACKUP_MIME_TYPE = "application/zip"

/** 자동 백업 기본 주기(시간). */
const val DEFAULT_AUTO_BACKUP_INTERVAL_HOURS = 24
const val DEFAULT_CLOUD_BACKUP_RETENTION = 7
val AUTO_BACKUP_INTERVAL_OPTIONS = listOf(6, 12, 24, 72)

data class DriveCloudBackupMetadata(
    val id: String,
    val name: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long,
) {
    val mode: BackupExportMode get() = DriveCloudSyncSupport.backupModeOf(name)
}

data class DriveCloudSyncStatus(
    val connectedEmail: String? = null,
    val latestBackup: DriveCloudBackupMetadata? = null,
    val isWorking: Boolean = false,
    val connectionError: String? = null,
) {
    /** 승인이 유효하고 최근 Drive 호출이 성공한 상태만 연결됨으로 본다. */
    val isConnected: Boolean = connectedEmail != null && connectionError == null
}

object DriveCloudSyncSupport {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
        .withZone(ZoneId.systemDefault())

    fun backupFileName(nowMillis: Long, mode: BackupExportMode = BackupExportMode.Full): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(nowMillis))
        val suffix = if (mode == BackupExportMode.Quick) QUICK_BACKUP_SUFFIX else ""
        return "$CLOUD_BACKUP_PREFIX$stamp$suffix$CLOUD_BACKUP_EXTENSION"
    }

    fun isMemorixCloudBackup(name: String?): Boolean =
        name?.startsWith(CLOUD_BACKUP_PREFIX) == true && name.endsWith(CLOUD_BACKUP_EXTENSION)

    fun backupModeOf(name: String): BackupExportMode =
        if (name.endsWith(QUICK_BACKUP_SUFFIX + CLOUD_BACKUP_EXTENSION)) BackupExportMode.Quick else BackupExportMode.Full

    /**
     * 복구 대상. 빠른 백업에는 원본(originals)이 없으므로 최신 전체 백업을 우선하고,
     * 전체 백업이 하나도 없을 때만 최신 빠른 백업을 쓴다. 입력은 최신순이어야 한다.
     */
    fun restoreCandidate(backupsNewestFirst: List<DriveCloudBackupMetadata>): DriveCloudBackupMetadata? =
        backupsNewestFirst.firstOrNull { it.mode == BackupExportMode.Full } ?: backupsNewestFirst.firstOrNull()

    fun isTransientDriveStatus(statusCode: Int): Boolean = statusCode == 408 || statusCode == 429 || statusCode >= 500

    /** Play 서비스 승인 오류 중 재시도로 풀릴 수 있는 것(7 = NETWORK_ERROR, 12502 = 응답 없음). */
    fun isTransientAuthorizationStatus(statusCode: Int): Boolean = statusCode == 7 || statusCode == 12502

    fun statusLabel(status: DriveCloudSyncStatus): String = when {
        status.connectionError != null -> "연결 확인 필요 · ${status.connectionError}"
        status.isWorking -> "연결 상태 확인 중..."
        status.connectedEmail == null -> "연결 안됨"
        status.latestBackup == null -> "연결됨 · ${status.connectedEmail} · 클라우드 백업 없음"
        else -> "연결됨 · ${status.connectedEmail} · 최근 백업 ${formatShortBytes(status.latestBackup.sizeBytes)}"
    }

    fun formatShortBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index += 1
        }
        return if (index == 0) "${bytes} B" else "${String.format(Locale.US, "%.1f", value)} ${units[index]}"
    }

    fun formatDateTime(millis: Long): String = timeFormatter.format(Instant.ofEpochMilli(millis))

    fun intervalLabel(hours: Int): String {
        val safe = hours.coerceAtLeast(1)
        return when {
            safe % 24 == 0 -> "${safe / 24}일마다"
            else -> "${safe}시간마다"
        }
    }

    fun intervalToMillis(hours: Int): Long = hours.coerceAtLeast(1) * 60L * 60L * 1000L

    /**
     * 보관 개수를 넘긴 오래된 백업 목록. 입력은 최신순으로 정렬되어 있어야 한다.
     *
     * 빠른 백업만 이어지면 원본이 들어 있는 전체 백업이 밀려 지워지므로, 최신 전체 백업 1개는 보관 개수와 별도로 남긴다.
     */
    fun backupsToPrune(
        backupsNewestFirst: List<DriveCloudBackupMetadata>,
        retentionCount: Int,
    ): List<DriveCloudBackupMetadata> {
        val newestFull = backupsNewestFirst.firstOrNull { it.mode == BackupExportMode.Full }
        return backupsNewestFirst
            .drop(retentionCount.coerceAtLeast(1))
            .filterNot { it == newestFull }
    }

    /**
     * 자동 백업을 건너뛸지 판단한다.
     *
     * - 직전 성공 이후 주기의 90%가 지나지 않았으면 건너뛴다(WorkManager flex로 일찍 깨는 경우 대비).
     * - 주기가 지났더라도 데이터 지문이 그대로면 업로드를 생략한다(같은 내용 재업로드 방지).
     */
    fun shouldSkipAutoBackup(
        nowMillis: Long,
        lastSuccessMillis: Long?,
        lastSignature: Long?,
        currentSignature: Long,
        intervalHours: Int,
    ): Boolean {
        if (lastSuccessMillis == null) return false
        val intervalMillis = intervalToMillis(intervalHours)
        val elapsed = nowMillis - lastSuccessMillis
        if (elapsed < intervalMillis * 9 / 10) return true
        // 지문이 같아도 안전장치로 최소 7일(주기가 길면 주기의 2배)마다 한 번은 새 백업을 만든다.
        val safetyMillis = maxOf(7L * 24 * 60 * 60 * 1000, intervalMillis * 2)
        return lastSignature != null && lastSignature == currentSignature && elapsed < safetyMillis
    }

    fun nextRunLabel(settings: CloudAutoBackupSettings): String? {
        if (!settings.enabled) return null
        val lastSuccess = settings.lastSuccessMillis ?: return "다음 자동 백업 대기 중"
        val nextMillis = lastSuccess + intervalToMillis(settings.intervalHours)
        return "다음 자동 백업 ${formatDateTime(nextMillis)} 이후"
    }

    fun autoBackupSummary(settings: CloudAutoBackupSettings): String {
        if (!settings.enabled) return "자동 백업 꺼짐"
        val lastSuccess = settings.lastSuccessMillis
            ?.let { "최근 성공 ${formatDateTime(it)}" }
            ?: "아직 자동 백업 없음"
        return "${intervalLabel(settings.intervalHours)} · $lastSuccess · ${retentionLabel(settings.retentionCount)}"
    }

    fun retentionLabel(retentionCount: Int): String =
        "최근 ${retentionCount.coerceAtLeast(1)}개 보관"

    fun mapAuthorizationError(statusCode: Int?, message: String?): String = when (statusCode) {
        10 -> "Google 로그인 설정 오류(코드 10). Google Cloud Console의 Android OAuth 클라이언트에 패키지명(com.mebonsoft.memorix)과 현재 APK의 SHA-1 지문이 등록되어 있는지 확인하세요."
        7 -> "네트워크 오류로 Google 계정 연결에 실패했습니다. 연결 상태를 확인한 뒤 다시 시도하세요."
        16, 12501 -> "Google 계정 연결이 취소되었습니다."
        12500 -> "Google 계정 연결에 실패했습니다. 기기의 Google Play 서비스를 최신 버전으로 업데이트한 뒤 다시 시도하세요."
        12502 -> "Google Play 서비스가 응답하지 않습니다. 잠시 후 다시 시도하세요."
        403 -> "Google 계정 접근이 거부되었습니다. OAuth 동의 화면의 테스트 사용자에 사용할 계정을 추가하거나 앱 검수를 완료해야 할 수 있습니다."
        401 -> "Google 인증이 만료되었습니다. 다시 연결해 주세요."
        else -> message?.takeIf { it.isNotBlank() }
            ?.let { "Google 계정 연결에 실패했습니다. ($it)" }
            ?: "Google 계정 연결에 실패했습니다."
    }

    fun mapDriveError(statusCode: Int?, message: String?): String = when (statusCode) {
        401 -> "Google Drive 인증이 만료되었습니다. 설정에서 Drive를 다시 연결해 주세요."
        403 -> "Google Drive 접근이 거부되었습니다. Drive 앱 전용 공간 권한이 취소되었을 수 있으니 다시 연결해 주세요."
        404 -> "Google Drive에서 백업 파일을 찾지 못했습니다."
        429, 500, 502, 503, 504 -> "Google Drive가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도하세요."
        else -> message?.takeIf { it.isNotBlank() }
            ?.let { "Google Drive 작업에 실패했습니다. ($it)" }
            ?: "Google Drive 작업에 실패했습니다."
    }
}
