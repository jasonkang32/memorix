package com.mebonsoft.memorix.core.cloud

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.mebonsoft.memorix.core.backup.BackupExportMode
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.google.api.services.drive.model.File as DriveFile

/** Drive 연결 시도 결과. 동의 화면이 필요하면 UI가 [ConsentRequired.intentSender]를 실행해야 한다. */
sealed interface DriveConnectOutcome {
    data class Connected(val status: DriveCloudSyncStatus) : DriveConnectOutcome

    data class ConsentRequired(val intentSender: IntentSender) : DriveConnectOutcome
}

data class AutoBackupResult(
    val skipped: Boolean,
    val message: String,
)

interface CloudSyncOperations {
    /** 승인 토큰을 요청한다. 이미 승인되어 있으면 조용히 연결 상태를 돌려준다. */
    suspend fun requestAuthorization(): DriveConnectOutcome

    /** 동의 화면 결과 Intent로 연결을 완료한다. */
    suspend fun completeAuthorization(resultIntent: Intent?): DriveCloudSyncStatus

    suspend fun refreshStatus(): DriveCloudSyncStatus

    /** 사용자가 직접 실행하는 즉시 백업. */
    suspend fun uploadCloudBackup(): DriveCloudSyncStatus

    /** WorkManager가 호출하는 자동 백업. 주기/변경 여부를 스스로 판단한다. */
    suspend fun runAutoBackup(nowMillis: Long = System.currentTimeMillis()): AutoBackupResult

    suspend fun restoreLatestCloudBackup(): DriveCloudSyncStatus

    suspend fun disconnect()
}

@Singleton
class GoogleDriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupOperations: MemorixBackupOperations,
    private val tokenProvider: DriveTokenProvider,
    private val preferences: CloudSyncPreferences,
) : CloudSyncOperations {
    /** 수동 백업·자동 백업·복구가 겹쳐 같은 임시 파일/DB를 동시에 다루지 않도록 직렬화한다. */
    private val operationLock = Mutex()

    override suspend fun requestAuthorization(): DriveConnectOutcome = withContext(Dispatchers.IO) {
        val outcome = when (val authorization = tokenProvider.authorize()) {
            is DriveAuthorization.Granted -> DriveConnectOutcome.Connected(
                connectWithToken(authorization.accessToken),
            )

            is DriveAuthorization.ConsentRequired -> DriveConnectOutcome.ConsentRequired(authorization.intentSender)

            is DriveAuthorization.Failed -> throw DriveSyncException(
                authorization.message,
                authorization.statusCode,
            )
        }
        Log.i(TAG, "requestAuthorization -> ${outcome.javaClass.simpleName}")
        outcome
    }

    override suspend fun completeAuthorization(resultIntent: Intent?): DriveCloudSyncStatus =
        withContext(Dispatchers.IO) {
            if (resultIntent == null) {
                throw DriveSyncException("Google 승인 화면이 결과를 주지 않고 닫혔습니다. 다시 시도해 주세요.")
            }
            val result = try {
                tokenProvider.resultFromIntent(resultIntent)
            } catch (error: ApiException) {
                throw DriveSyncException(
                    DriveCloudSyncSupport.mapAuthorizationError(error.statusCode, error.localizedMessage),
                    error.statusCode,
                )
            }
            when (val authorization = tokenProvider.toAuthorization(result)) {
                is DriveAuthorization.Granted -> connectWithToken(authorization.accessToken)
                is DriveAuthorization.ConsentRequired ->
                    throw DriveSyncException("Google Drive 권한 승인이 완료되지 않았습니다. 다시 시도해 주세요.")
                is DriveAuthorization.Failed -> throw DriveSyncException(authorization.message, authorization.statusCode)
            }
        }

    override suspend fun refreshStatus(): DriveCloudSyncStatus = withContext(Dispatchers.IO) {
        val storedEmail = preferences.currentConnectedEmail() ?: return@withContext DriveCloudSyncStatus()
        try {
            val token = tokenProvider.silentAccessToken()
                ?: return@withContext DriveCloudSyncStatus(
                    connectedEmail = storedEmail,
                    connectionError = "Google Drive 권한을 다시 승인해야 합니다.",
                )
            val latest = runDrive(token) { service -> latestBackup(service) }
            DriveCloudSyncStatus(connectedEmail = storedEmail, latestBackup = latest)
        } catch (error: Exception) {
            DriveCloudSyncStatus(connectedEmail = storedEmail, connectionError = error.toCloudMessage())
        }
    }

    override suspend fun uploadCloudBackup(): DriveCloudSyncStatus = withContext(Dispatchers.IO) {
        operationLock.withLock { uploadCloudBackupLocked() }
    }

    private suspend fun uploadCloudBackupLocked(): DriveCloudSyncStatus {
        val email = preferences.currentConnectedEmail()
            ?: throw DriveSyncException("먼저 Google Drive를 연결하세요.")
        val mode = preferences.currentAutoBackup().mode
        val token = requireToken()
        val now = System.currentTimeMillis()
        val uploaded = uploadBackup(token, now, mode)
        pruneOldBackups(token)
        preferences.recordSuccess(now, backupOperations.calculateChangeSignature(), null)
        return DriveCloudSyncStatus(connectedEmail = email, latestBackup = uploaded)
    }

    override suspend fun runAutoBackup(nowMillis: Long): AutoBackupResult = withContext(Dispatchers.IO) {
        operationLock.withLock { runAutoBackupLocked(nowMillis) }
    }

    private suspend fun runAutoBackupLocked(nowMillis: Long): AutoBackupResult {
        val settings = preferences.currentAutoBackup()
        if (!settings.enabled) return AutoBackupResult(true, "자동 백업이 꺼져 있습니다.")
        preferences.currentConnectedEmail()
            ?: return AutoBackupResult(true, "Google Drive가 연결되어 있지 않습니다.")

        val signature = backupOperations.calculateChangeSignature()
        val skip = DriveCloudSyncSupport.shouldSkipAutoBackup(
            nowMillis = nowMillis,
            lastSuccessMillis = settings.lastSuccessMillis,
            lastSignature = settings.lastSignature,
            currentSignature = signature,
            intervalHours = settings.intervalHours,
        )
        if (skip) {
            return AutoBackupResult(true, "변경된 데이터가 없어 자동 백업을 건너뛰었습니다.")
        }

        preferences.recordAttempt(nowMillis, null)
        return try {
            val token = tokenProvider.silentAccessToken()
                ?: throw DriveSyncException("Google Drive 권한을 다시 승인해야 합니다.")
            val uploaded = uploadBackup(token, nowMillis, settings.mode)
            pruneOldBackups(token)
            // export 중 DB 체크포인트로 파일 지문이 바뀌므로, 업로드 직후 상태를 저장해 다음 실행에서 재업로드를 막는다.
            val signatureAfterUpload = backupOperations.calculateChangeSignature()
            val message = "자동 백업 완료 · ${DriveCloudSyncSupport.formatShortBytes(uploaded.sizeBytes)}"
            preferences.recordSuccess(nowMillis, signatureAfterUpload, message)
            AutoBackupResult(
                skipped = false,
                message = message,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.toCloudMessage()
            preferences.recordFailure(nowMillis, message)
            throw DriveSyncException(
                message = message,
                statusCode = (error as? DriveSyncException)?.statusCode,
                transient = (error as? DriveSyncException)?.transient == true,
            )
        }
    }

    override suspend fun restoreLatestCloudBackup(): DriveCloudSyncStatus = withContext(Dispatchers.IO) {
        operationLock.withLock { restoreLatestCloudBackupLocked() }
    }

    private suspend fun restoreLatestCloudBackupLocked(): DriveCloudSyncStatus {
        val email = preferences.currentConnectedEmail()
            ?: throw DriveSyncException("먼저 Google Drive를 연결하세요.")
        require(!backupOperations.hasLocalData()) {
            "현재 기기에 기록이 있어 Drive 복구를 중단했습니다. 먼저 현재 데이터를 별도 백업하거나 초기화한 뒤 다시 시도하세요."
        }
        val token = requireToken()
        val target = runDrive(token) { service -> DriveCloudSyncSupport.restoreCandidate(listBackups(service)) }
            ?: throw DriveSyncException("Google Drive에 Memorix 백업이 없습니다.")
        Log.i(TAG, "restore target=${target.name} mode=${target.mode}")
        val tempFile = File(context.cacheDir, "restore_${target.id}.zip")
        tempFile.delete()
        try {
            runDrive(token) { service ->
                // 401 재시도 시 앞서 받은 일부 바이트가 남지 않도록 매 시도마다 파일을 새로 연다.
                tempFile.outputStream().use { output ->
                    service.files().get(target.id).executeMediaAndDownloadTo(output)
                }
            }
            backupOperations.restoreBackupFromFile(tempFile)
            return DriveCloudSyncStatus(connectedEmail = email, latestBackup = target)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        tokenProvider.revoke()
        preferences.clearConnection()
    }

    private suspend fun connectWithToken(accessToken: String): DriveCloudSyncStatus {
        val email = runDrive(accessToken) { service -> accountEmail(service) } ?: "Google 계정"
        Log.i(TAG, "connected account=$email")
        ensureConnectionReadme(accessToken)
        preferences.setConnectedEmail(email)
        val latest = runDrive(accessToken) { service -> latestBackup(service) }
        return DriveCloudSyncStatus(connectedEmail = email, latestBackup = latest)
    }

    private suspend fun requireToken(): String {
        return try {
            tokenProvider.silentAccessToken()
        } catch (error: DriveSyncException) {
            throw error
        } catch (error: Exception) {
            throw DriveSyncException(error.toCloudMessage())
        } ?: throw DriveSyncException("Google Drive 권한을 다시 승인해야 합니다. 설정에서 Drive를 다시 연결해 주세요.")
    }

    private suspend fun uploadBackup(
        token: String,
        nowMillis: Long,
        mode: BackupExportMode,
    ): DriveCloudBackupMetadata {
        val tempFile = File(context.cacheDir, DriveCloudSyncSupport.backupFileName(nowMillis, mode))
        tempFile.delete()
        try {
            backupOperations.exportBackupToFile(tempFile, mode)
            val metadata = DriveFile().apply {
                name = tempFile.name
                parents = listOf("appDataFolder")
                mimeType = MEMORIX_CLOUD_BACKUP_MIME_TYPE
            }
            val created = runDrive(token) { service ->
                service.files()
                    .create(metadata, FileContent(MEMORIX_CLOUD_BACKUP_MIME_TYPE, tempFile))
                    .setFields("id,name,modifiedTime,size")
                    .execute()
            }
            return DriveCloudBackupMetadata(
                id = created.id,
                name = created.name,
                modifiedTimeMillis = created.modifiedTime?.value ?: nowMillis,
                sizeBytes = created.getSize() ?: tempFile.length(),
            )
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun pruneOldBackups(token: String) {
        val retention = preferences.currentAutoBackup().retentionCount
        val backups = runDrive(token) { service -> listBackups(service) }
        val targets = DriveCloudSyncSupport.backupsToPrune(backups, retention)
        targets.forEach { backup ->
            runCatching {
                runDrive(token) { service -> service.files().delete(backup.id).execute() }
            }
        }
    }

    private suspend fun ensureConnectionReadme(token: String) {
        val fileName = "Memorix_README.txt"
        val existing = runDrive(token) { service ->
            service.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$fileName' and trashed=false")
                .setFields("files(id)")
                .execute()
                .files
                .orEmpty()
        }
        if (existing.isNotEmpty()) return

        val readme = """
            Memorix Google Drive connection test

            This file confirms that Memorix connected to Google Drive and can access its app-private storage.

            Storage location: Google Drive appDataFolder (hidden from the regular My Drive file list).
            This file contains no personal records or secrets.
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val metadata = DriveFile().apply {
            name = fileName
            parents = listOf("appDataFolder")
            mimeType = "text/plain"
        }
        runDrive(token) { service ->
            service.files().create(
                metadata,
                InputStreamContent("text/plain", ByteArrayInputStream(readme)),
            ).setFields("id,name").execute()
        }
    }

    private fun accountEmail(service: Drive): String? =
        service.about()
            .get()
            .setFields("user(emailAddress,displayName)")
            .execute()
            .user
            ?.let { user -> user.emailAddress?.takeIf { it.isNotBlank() } ?: user.displayName }

    private fun listBackups(service: Drive): List<DriveCloudBackupMetadata> {
        val files = service.files().list()
            .setSpaces("appDataFolder")
            .setQ("mimeType='application/zip' and trashed=false")
            .setOrderBy("modifiedTime desc")
            .setPageSize(100)
            .setFields("files(id,name,modifiedTime,size)")
            .execute()
            .files
            .orEmpty()
        return files
            .filter { DriveCloudSyncSupport.isMemorixCloudBackup(it.name) }
            .map { file ->
                DriveCloudBackupMetadata(
                    id = file.id,
                    name = file.name,
                    modifiedTimeMillis = file.modifiedTime?.value ?: 0L,
                    sizeBytes = file.getSize() ?: 0L,
                )
            }
            .sortedByDescending { it.modifiedTimeMillis }
    }

    private fun latestBackup(service: Drive): DriveCloudBackupMetadata? = listBackups(service).firstOrNull()

    /**
     * Drive 호출 공통 래퍼. 토큰 만료(401)면 캐시를 비우고 한 번만 재시도한다.
     */
    private suspend fun <T> runDrive(token: String, block: suspend (Drive) -> T): T {
        return try {
            mapDriveErrors { block(driveService(token)) }
        } catch (error: DriveSyncException) {
            if (error.statusCode != 401) throw error
            Log.w(TAG, "drive 401, refreshing token")
            tokenProvider.invalidateToken(token)
            val refreshed = requireToken()
            mapDriveErrors { block(driveService(refreshed)) }
        }
    }

    /** Drive 클라이언트 예외를 화면에 보일 메시지와 재시도 여부가 담긴 [DriveSyncException]으로 바꾼다. */
    private suspend fun <T> mapDriveErrors(call: suspend () -> T): T {
        return try {
            call()
        } catch (error: GoogleJsonResponseException) {
            Log.w(TAG, "drive error status=${error.statusCode} msg=${error.details?.message}", error)
            throw DriveSyncException(
                message = DriveCloudSyncSupport.mapDriveError(error.statusCode, error.details?.message),
                statusCode = error.statusCode,
                transient = DriveCloudSyncSupport.isTransientDriveStatus(error.statusCode),
            )
        } catch (error: IOException) {
            Log.w(TAG, "drive io error", error)
            throw DriveSyncException(
                message = DriveCloudSyncSupport.mapDriveError(null, error.localizedMessage),
                transient = true,
            )
        }
    }

    private fun driveService(accessToken: String): Drive =
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), BearerTokenInitializer(accessToken))
            .setApplicationName("Memorix")
            .build()
}

private class BearerTokenInitializer(private val accessToken: String) : HttpRequestInitializer {
    override fun initialize(request: HttpRequest) {
        request.headers.authorization = "Bearer $accessToken"
    }
}

internal fun Throwable.toCloudMessage(): String {
    val driveError = this as? GoogleJsonResponseException
    val apiError = this as? ApiException
    return when {
        this is DriveSyncException -> message ?: "Google Drive 작업에 실패했습니다."
        driveError != null -> DriveCloudSyncSupport.mapDriveError(driveError.statusCode, driveError.details?.message)
        apiError != null -> DriveCloudSyncSupport.mapAuthorizationError(apiError.statusCode, apiError.localizedMessage)
        else -> DriveCloudSyncSupport.mapDriveError(null, localizedMessage)
    }
}

private const val TAG = "MemorixDrive"
