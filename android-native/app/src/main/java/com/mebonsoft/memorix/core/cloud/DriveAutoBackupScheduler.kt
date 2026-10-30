package com.mebonsoft.memorix.core.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive 자동 백업 WorkManager 스케줄러.
 *
 * 주기 변경/켜기/끄기는 같은 unique work를 UPDATE 정책으로 갱신하고, 끄면 취소한다.
 */
@Singleton
class DriveAutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun sync(settings: CloudAutoBackupSettings) {
        if (!settings.enabled) {
            cancel()
            return
        }
        val request = PeriodicWorkRequestBuilder<DriveAutoBackupWorker>(
            settings.intervalHours.toLong().coerceAtLeast(1),
            TimeUnit.HOURS,
        )
            .setConstraints(settings.constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_NAME)
    }

    private fun CloudAutoBackupSettings.constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    companion object {
        const val TAG = "memorix_drive_auto_backup"
        const val UNIQUE_PERIODIC_NAME = "memorix_drive_auto_backup_periodic"
    }
}
