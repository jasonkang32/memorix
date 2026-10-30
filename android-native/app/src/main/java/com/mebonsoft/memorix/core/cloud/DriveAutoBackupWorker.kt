package com.mebonsoft.memorix.core.cloud

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mebonsoft.memorix.core.monetization.EntitlementPolicy
import com.mebonsoft.memorix.core.monetization.FeatureGateDecision
import com.mebonsoft.memorix.core.monetization.ProEntitlementRepository
import com.mebonsoft.memorix.core.monetization.ProFeature
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 주기적으로 Google Drive에 백업을 올린다.
 *
 * 업로드 여부(주기/데이터 변경/권한)는 [CloudSyncOperations.runAutoBackup]이 판단하고,
 * 이 Worker는 Pro 권한 확인과 재시도 정책만 담당한다.
 */
@HiltWorker
class DriveAutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cloudSync: CloudSyncOperations,
    private val entitlementRepository: ProEntitlementRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entitlement = entitlementRepository.entitlement.first()
        val decision = EntitlementPolicy.canUseFeature(entitlement, ProFeature.CloudSync)
        if (decision is FeatureGateDecision.UpgradeRequired) {
            return Result.success()
        }
        return try {
            cloudSync.runAutoBackup()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: DriveSyncException) {
            // 권한 취소 같은 영구 오류는 재시도해도 같으므로 다음 주기까지 기다린다(사유는 설정 화면에 기록됨).
            if (error.transient && runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }
    }
}

private const val MAX_RETRY_ATTEMPTS = 5
