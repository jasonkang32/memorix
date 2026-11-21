package com.mebonsoft.memorix.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mebonsoft.memorix.core.cloud.CloudSyncPreferences
import com.mebonsoft.memorix.core.cloud.DriveAutoBackupScheduler
import com.mebonsoft.memorix.core.media.ThumbnailOrientationRepairRunner
import com.mebonsoft.memorix.core.security.ProtectedMediaSession
import com.mebonsoft.memorix.core.security.SecretFileFetcher
import com.mebonsoft.memorix.core.security.SecretMediaMigrationRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MemorixApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var secretMediaMigrationRunner: SecretMediaMigrationRunner
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cloudSyncPreferences: CloudSyncPreferences
    @Inject lateinit var autoBackupScheduler: DriveAutoBackupScheduler
    @Inject lateinit var thumbnailOrientationRepairRunner: ThumbnailOrientationRepairRunner
    @Inject lateinit var protectedMediaSession: ProtectedMediaSession

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // 잠금·비밀 항목의 암호화 파일(.mrxsecret)도 미리보기되도록 복호화 Fetcher를 등록한다.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SecretFileFetcher.Factory(this@MemorixApplication)) }
        .build()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = protectedMediaSession.onActivityStarted(System.currentTimeMillis())
            override fun onActivityStopped(activity: Activity) = protectedMediaSession.onActivityStopped(System.currentTimeMillis())
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        applicationScope.launch {
            protectedMediaSession.clearPlainViewCacheOnStart()
            runCatching { secretMediaMigrationRunner.hardenExistingSecretFiles() }
            runCatching { thumbnailOrientationRepairRunner.repairOnce() }
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
