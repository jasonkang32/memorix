package com.mebonsoft.memorix.core.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent

import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.api.services.drive.model.File as DriveFile

interface CloudSyncOperations {
    fun createSignInIntent(): Intent
    suspend fun handleSignInResult(data: Intent?): DriveCloudSyncStatus
    suspend fun refreshStatus(): DriveCloudSyncStatus
    suspend fun uploadCloudBackup(): DriveCloudSyncStatus
    suspend fun restoreLatestCloudBackup(): DriveCloudSyncStatus
    suspend fun disconnect()
}

@Singleton
class GoogleDriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupOperations: MemorixBackupOperations,
) : CloudSyncOperations {
    private val scope = DriveScopes.DRIVE_APPDATA

    override fun createSignInIntent(): Intent = signInClient().signInIntent

    override suspend fun handleSignInResult(data: Intent?): DriveCloudSyncStatus = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        require(account != null) { "Google 계정 연결에 실패했습니다." }
        refreshStatusFor(account)
    }

    override suspend fun refreshStatus(): DriveCloudSyncStatus = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null || !GoogleSignIn.hasPermissions(account, Scope(scope))) {
            DriveCloudSyncStatus()
        } else {
            refreshStatusFor(account)
        }
    }

    override suspend fun uploadCloudBackup(): DriveCloudSyncStatus = withContext(Dispatchers.IO) {
        val account = signedInAccountOrError()
        val tempFile = File(context.cacheDir, DriveCloudSyncSupport.backupFileName(System.currentTimeMillis()))
        tempFile.delete()
        try {
            backupOperations.exportBackupToFile(tempFile)
            val metadata = DriveFile().apply {
                name = tempFile.name
                parents = listOf("appDataFolder")
                mimeType = MEMORIX_CLOUD_BACKUP_MIME_TYPE
            }
            driveService(account).files().create(metadata, FileContent(MEMORIX_CLOUD_BACKUP_MIME_TYPE, tempFile))
                .setFields("id,name,modifiedTime,size")
                .execute()
            refreshStatusFor(account)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun restoreLatestCloudBackup(): DriveCloudSyncStatus = withContext(Dispatchers.IO) {
        val account = signedInAccountOrError()
        val latest = latestBackup(driveService(account)) ?: error("Google Drive에 Memorix 백업이 없습니다.")
        val tempFile = File(context.cacheDir, "restore_${latest.name}")
        tempFile.delete()
        try {
            tempFile.outputStream().use { output ->
                driveService(account).files().get(latest.id).executeMediaAndDownloadTo(output)
            }
            backupOperations.restoreBackupFromFile(tempFile)
            refreshStatusFor(account)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) { signInClient().signOut().awaitTask() }
    }

    private fun signedInAccountOrError(): GoogleSignInAccount {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        require(account != null && GoogleSignIn.hasPermissions(account, Scope(scope))) {
            "먼저 Google Drive를 연결하세요."
        }
        return account
    }

    private fun refreshStatusFor(account: GoogleSignInAccount): DriveCloudSyncStatus {
        val latest = runCatching { latestBackup(driveService(account)) }.getOrNull()
        return DriveCloudSyncStatus(
            connectedEmail = account.email ?: account.displayName ?: "Google 계정",
            latestBackup = latest,
        )
    }

    private fun latestBackup(service: Drive): DriveCloudBackupMetadata? {
        val result = service.files().list()
            .setSpaces("appDataFolder")
            .setQ("mimeType='application/zip' and trashed=false")
            .setOrderBy("modifiedTime desc")
            .setFields("files(id,name,modifiedTime,size)")
            .execute()
        return result.files.orEmpty()
            .firstOrNull { DriveCloudSyncSupport.isMemorixCloudBackup(it.name) }
            ?.let { file ->
                DriveCloudBackupMetadata(
                    id = file.id,
                    name = file.name,
                    modifiedTimeMillis = file.modifiedTime?.value ?: 0L,
                    sizeBytes = file.getSize() ?: 0L,
                )
            }
    }

    private fun driveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(scope)).apply {
            selectedAccount = account.account
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Memorix")
            .build()
    }

    private fun signInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(scope))
            .build()
        return GoogleSignIn.getClient(context, options)
    }
}

private fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T? {
    while (!isComplete) {
        Thread.sleep(20)
    }
    if (isSuccessful) return result
    throw exception ?: IllegalStateException("Google 작업에 실패했습니다.")
}
