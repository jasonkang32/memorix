package com.mebonsoft.memorix.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mebonsoft.memorix.core.cloud.CloudSyncPreferences
import com.mebonsoft.memorix.core.cloud.DriveAutoBackupScheduler
import com.mebonsoft.memorix.core.security.SecretMediaMigrationRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MemorixApplication : Application(), Configuration.Provider {
    @Inject lateinit var secretMediaMigrationRunner: SecretMediaMigrationRunner
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cloudSyncPreferences: CloudSyncPreferences
    @Inject lateinit var autoBackupScheduler: DriveAutoBackupScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { secretMediaMigrationRunner.hardenExistingSecretFiles() }
            runCatching {
                // 앱 업데이트/재부팅 후에도 자동 백업 스케줄이 유지되도록 복원한다.
                val settings = cloudSyncPreferences.currentAutoBackup()
                if (settings.enabled && cloudSyncPreferences.currentConnectedEmail() != null) {
                    autoBackupScheduler.sync(settings)
                }
            }
        }
    }
}
