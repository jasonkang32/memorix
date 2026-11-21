package com.mebonsoft.memorix.core.cloud

import com.mebonsoft.memorix.core.backup.BackupExportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCloudSyncSupportTest {
    @Test
    fun backupFileName_usesMemorixZipPrefix() {
        val name = DriveCloudSyncSupport.backupFileName(1_700_000_000_000L)

        assertTrue(name.startsWith("Memorix_Cloud_"))
        assertTrue(name.endsWith(".zip"))
        assertTrue(DriveCloudSyncSupport.isMemorixCloudBackup(name))
        assertFalse(DriveCloudSyncSupport.isMemorixCloudBackup("other.zip"))
    }

    @Test
    fun statusLabel_showsConnectedAndLatestState() {
        assertEquals(
            "연결 안됨",
            DriveCloudSyncSupport.statusLabel(DriveCloudSyncStatus()),
        )
        assertEquals(
            "연결됨 · jk@example.com · 클라우드 백업 없음",
            DriveCloudSyncSupport.statusLabel(DriveCloudSyncStatus(connectedEmail = "jk@example.com")),
        )
        assertEquals(
            "연결됨 · jk@example.com · 최근 백업 1.0 MB",
            DriveCloudSyncSupport.statusLabel(
                DriveCloudSyncStatus(
                    connectedEmail = "jk@example.com",
                    latestBackup = DriveCloudBackupMetadata("id", "backup.zip", 0L, 1_048_576L),
                ),
            ),
        )
    }

    @Test
    fun statusLabel_prioritizesConnectionError() {
        val label = DriveCloudSyncSupport.statusLabel(
            DriveCloudSyncStatus(
                connectedEmail = "jk@example.com",
                connectionError = "Google Drive 권한을 다시 승인해야 합니다.",
            ),
        )

        assertTrue(label.startsWith("연결 확인 필요"))
        assertTrue(label.contains("다시 승인"))
    }

    @Test
    fun backupsToPrune_keepsNewestAndDropsOverflow() {
        val backups = (1..5).map { index ->
            DriveCloudBackupMetadata(
                id = "id$index",
                name = "Memorix_Cloud_$index.zip",
                modifiedTimeMillis = 1_000L - index,
                sizeBytes = index.toLong(),
            )
        }

        val pruned = DriveCloudSyncSupport.backupsToPrune(backups, retentionCount = 3)

        assertEquals(listOf("id4", "id5"), pruned.map { it.id })
        assertTrue(DriveCloudSyncSupport.backupsToPrune(backups, retentionCount = 9).isEmpty())
        assertEquals(4, DriveCloudSyncSupport.backupsToPrune(backups, retentionCount = 0).size)
    }

    @Test
    fun shouldSkipAutoBackup_respectsInterval() {
        val now = 10_000_000L
        val intervalHours = 24
        val intervalMillis = DriveCloudSyncSupport.intervalToMillis(intervalHours)

        // 한 번도 성공한 적 없으면 실행한다.
        assertFalse(
            DriveCloudSyncSupport.shouldSkipAutoBackup(
                nowMillis = now,
                lastSuccessMillis = null,
                lastSignature = 1L,
                currentSignature = 1L,
                intervalHours = intervalHours,
            ),
        )

        // 주기 이내면 건너뛴다.
        assertTrue(
            DriveCloudSyncSupport.shouldSkipAutoBackup(
                nowMillis = now,
                lastSuccessMillis = now - intervalMillis / 2,
                lastSignature = 1L,
                currentSignature = 1L,
                intervalHours = intervalHours,
            ),
        )

        // 주기가 지났고 데이터가 바뀌었으면 실행한다.
        assertFalse(
            DriveCloudSyncSupport.shouldSkipAutoBackup(
                nowMillis = now,
                lastSuccessMillis = now - intervalMillis - 1,
                lastSignature = 1L,
                currentSignature = 2L,
                intervalHours = intervalHours,
            ),
        )

        // 주기가 지났어도 데이터가 그대로면 업로드를 생략한다.
        assertTrue(
            DriveCloudSyncSupport.shouldSkipAutoBackup(
                nowMillis = now,
                lastSuccessMillis = now - intervalMillis - 1,
                lastSignature = 7L,
                currentSignature = 7L,
                intervalHours = intervalHours,
            ),
        )
    }

    @Test
    fun intervalLabel_formatsHoursAndDays() {
        assertEquals("6시간마다", DriveCloudSyncSupport.intervalLabel(6))
        assertEquals("12시간마다", DriveCloudSyncSupport.intervalLabel(12))
        assertEquals("1일마다", DriveCloudSyncSupport.intervalLabel(24))
        assertEquals("3일마다", DriveCloudSyncSupport.intervalLabel(72))
    }

    @Test
    fun autoBackupSummary_describesDisabledAndEnabledStates() {
        assertEquals(
            "자동 백업 꺼짐",
            DriveCloudSyncSupport.autoBackupSummary(CloudAutoBackupSettings()),
        )

        val summary = DriveCloudSyncSupport.autoBackupSummary(
            CloudAutoBackupSettings(
                enabled = true,
                intervalHours = 24,
                retentionCount = 5,
                mode = BackupExportMode.Quick,
            ),
        )
        assertTrue(summary.contains("1일마다"))
        assertTrue(summary.contains("아직 자동 백업 없음"))
        assertTrue(summary.contains("최근 5개 보관"))
    }

    @Test
    fun nextRunLabel_returnsNullWhenDisabled() {
        assertEquals(null, DriveCloudSyncSupport.nextRunLabel(CloudAutoBackupSettings()))
        assertEquals(
            "다음 자동 백업 대기 중",
            DriveCloudSyncSupport.nextRunLabel(CloudAutoBackupSettings(enabled = true)),
        )
    }

    @Test
    fun status_isConnectedFalseWhenConsentMustBeRenewed() {
        assertTrue(DriveCloudSyncStatus(connectedEmail = "jk@example.com").isConnected)
        assertFalse(
            DriveCloudSyncStatus(
                connectedEmail = "jk@example.com",
                connectionError = "Google Drive 권한을 다시 승인해야 합니다.",
            ).isConnected,
        )
    }

    @Test
    fun mapAuthorizationError_explainsDeveloperAndConsentErrors() {
        assertTrue(DriveCloudSyncSupport.mapAuthorizationError(10, null).contains("SHA-1"))
        assertEquals("Google 계정 연결이 취소되었습니다.", DriveCloudSyncSupport.mapAuthorizationError(12501, null))
        assertTrue(DriveCloudSyncSupport.mapAuthorizationError(403, null).contains("테스트 사용자"))
        assertEquals(
            "Google 계정 연결에 실패했습니다.",
            DriveCloudSyncSupport.mapAuthorizationError(null, null),
        )
    }

    @Test
    fun mapDriveError_explainsExpiredAuthAndTransientFailures() {
        assertTrue(DriveCloudSyncSupport.mapDriveError(401, null).contains("다시 연결"))
        assertTrue(DriveCloudSyncSupport.mapDriveError(403, null).contains("권한"))
        assertTrue(DriveCloudSyncSupport.mapDriveError(503, null).contains("일시적"))
        assertEquals("Google Drive 작업에 실패했습니다.", DriveCloudSyncSupport.mapDriveError(null, ""))
    }

    @Test
    fun backupFileName_marksQuickModeAndParsesItBack() {
        val full = DriveCloudSyncSupport.backupFileName(1_700_000_000_000L, BackupExportMode.Full)
        val quick = DriveCloudSyncSupport.backupFileName(1_700_000_000_000L, BackupExportMode.Quick)

        assertTrue(quick.endsWith("_quick.zip"))
        assertTrue(DriveCloudSyncSupport.isMemorixCloudBackup(quick))
        assertEquals(BackupExportMode.Full, DriveCloudSyncSupport.backupModeOf(full))
        assertEquals(BackupExportMode.Quick, DriveCloudSyncSupport.backupModeOf(quick))
        // 접미사가 없던 이전 버전 백업은 전체 백업으로 취급한다.
        assertEquals(BackupExportMode.Full, DriveCloudSyncSupport.backupModeOf("Memorix_Cloud_20260701_120000.zip"))
    }

    @Test
    fun backupsToPrune_alwaysKeepsNewestFullBackup() {
        // 최신순: quick 4개 뒤에 full 1개, 그 뒤에 더 오래된 full 1개
        val backups = listOf(
            backup("q1", quick = true),
            backup("q2", quick = true),
            backup("q3", quick = true),
            backup("q4", quick = true),
            backup("f1", quick = false),
            backup("f2", quick = false),
        )

        val pruned = DriveCloudSyncSupport.backupsToPrune(backups, retentionCount = 2)

        assertEquals(listOf("q3", "q4", "f2"), pruned.map { it.id })
    }

    @Test
    fun restoreCandidate_prefersNewestFullBackup() {
        val withFull = listOf(backup("q1", quick = true), backup("f1", quick = false), backup("f2", quick = false))
        val quickOnly = listOf(backup("q1", quick = true), backup("q2", quick = true))

        assertEquals("f1", DriveCloudSyncSupport.restoreCandidate(withFull)?.id)
        assertEquals("q1", DriveCloudSyncSupport.restoreCandidate(quickOnly)?.id)
        assertEquals(null, DriveCloudSyncSupport.restoreCandidate(emptyList()))
    }

    @Test
    fun isTransientStatus_retriesOnlyServerAndRateLimitErrors() {
        listOf(408, 429, 500, 502, 503, 504).forEach { assertTrue(DriveCloudSyncSupport.isTransientDriveStatus(it)) }
        listOf(400, 401, 403, 404).forEach { assertFalse(DriveCloudSyncSupport.isTransientDriveStatus(it)) }
        assertTrue(DriveCloudSyncSupport.isTransientAuthorizationStatus(7))
        assertTrue(DriveCloudSyncSupport.isTransientAuthorizationStatus(12502))
        assertFalse(DriveCloudSyncSupport.isTransientAuthorizationStatus(10))
    }

    private fun backup(id: String, quick: Boolean) = DriveCloudBackupMetadata(
        id = id,
        name = if (quick) "Memorix_Cloud_${id}_quick.zip" else "Memorix_Cloud_$id.zip",
        modifiedTimeMillis = 0L,
        sizeBytes = 1L,
    )
}
