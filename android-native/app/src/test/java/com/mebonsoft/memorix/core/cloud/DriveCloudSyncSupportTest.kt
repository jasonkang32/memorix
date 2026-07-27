package com.mebonsoft.memorix.core.cloud

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
            "Google Drive를 연결하면 백업을 내 계정 클라우드에 보관",
            DriveCloudSyncSupport.statusLabel(DriveCloudSyncStatus()),
        )
        assertEquals(
            "jk@example.com 연결됨 · 아직 클라우드 백업 없음",
            DriveCloudSyncSupport.statusLabel(DriveCloudSyncStatus(connectedEmail = "jk@example.com")),
        )
    }
}
