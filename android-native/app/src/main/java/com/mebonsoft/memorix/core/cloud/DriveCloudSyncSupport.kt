package com.mebonsoft.memorix.core.cloud

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CLOUD_BACKUP_PREFIX = "Memorix_Cloud_"
private const val CLOUD_BACKUP_EXTENSION = ".zip"
const val MEMORIX_CLOUD_BACKUP_MIME_TYPE = "application/zip"

data class DriveCloudBackupMetadata(
    val id: String,
    val name: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long,
)

data class DriveCloudSyncStatus(
    val connectedEmail: String? = null,
    val latestBackup: DriveCloudBackupMetadata? = null,
    val isWorking: Boolean = false,
) {
    val isConnected: Boolean = connectedEmail != null
}

object DriveCloudSyncSupport {
    fun backupFileName(nowMillis: Long): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(nowMillis))
        return "$CLOUD_BACKUP_PREFIX$stamp$CLOUD_BACKUP_EXTENSION"
    }

    fun isMemorixCloudBackup(name: String?): Boolean =
        name?.startsWith(CLOUD_BACKUP_PREFIX) == true && name.endsWith(CLOUD_BACKUP_EXTENSION)

    fun statusLabel(status: DriveCloudSyncStatus): String = when {
        status.isWorking -> "Google Drive 처리 중..."
        status.connectedEmail == null -> "Google Drive를 연결하면 백업을 내 계정 클라우드에 보관"
        status.latestBackup == null -> "${status.connectedEmail} 연결됨 · 아직 클라우드 백업 없음"
        else -> "${status.connectedEmail} 연결됨 · 최근 백업 ${formatShortBytes(status.latestBackup.sizeBytes)}"
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
        return if (index == 0) "${bytes} B" else "${String.format(java.util.Locale.US, "%.1f", value)} ${units[index]}"
    }
}
