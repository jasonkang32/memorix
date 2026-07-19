package com.mebonsoft.memorix.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ImportMediaWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // 다음 단계에서 URI 목록 deserialize 후 MediaImportManager로 연결한다.
        return Result.success()
    }
}
