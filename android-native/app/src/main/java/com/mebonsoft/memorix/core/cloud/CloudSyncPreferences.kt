package com.mebonsoft.memorix.core.cloud

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mebonsoft.memorix.core.backup.BackupExportMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudSyncDataStore by preferencesDataStore(name = "memorix_cloud_sync")

data class CloudAutoBackupSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = DEFAULT_AUTO_BACKUP_INTERVAL_HOURS,
    val wifiOnly: Boolean = true,
    val retentionCount: Int = DEFAULT_CLOUD_BACKUP_RETENTION,
    val mode: BackupExportMode = BackupExportMode.Full,
    val lastSuccessMillis: Long? = null,
    val lastAttemptMillis: Long? = null,
    val lastSignature: Long? = null,
    val lastMessage: String? = null,
)

/**
 * Google Drive 연결 상태와 자동 백업 설정을 보관한다.
 *
 * 액세스 토큰은 저장하지 않는다. AuthorizationClient가 토큰 캐시를 관리하므로
 * 필요할 때마다 조용히(silent) 새 토큰을 받아 쓴다.
 */
@Singleton
class CloudSyncPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ConnectedEmail = stringPreferencesKey("connected_email")
        val AutoEnabled = booleanPreferencesKey("auto_backup_enabled")
        val AutoIntervalHours = intPreferencesKey("auto_backup_interval_hours")
        val AutoWifiOnly = booleanPreferencesKey("auto_backup_wifi_only")
        val AutoRetention = intPreferencesKey("auto_backup_retention")
        val AutoMode = stringPreferencesKey("auto_backup_mode")
        val LastSuccess = longPreferencesKey("auto_backup_last_success")
        val LastAttempt = longPreferencesKey("auto_backup_last_attempt")
        val LastSignature = longPreferencesKey("auto_backup_last_signature")
        val LastMessage = stringPreferencesKey("auto_backup_last_message")
        val LastError = stringPreferencesKey("connect_last_error")
        val LastErrorAt = longPreferencesKey("connect_last_error_at")
    }

    val connectedEmail: Flow<String?> = context.cloudSyncDataStore.data
        .map { preferences -> preferences[Keys.ConnectedEmail]?.takeIf { it.isNotBlank() } }

    /** 마지막 연결 실패 사유. 화면에 고정 표시해 원인을 바로 볼 수 있게 한다. */
    val connectError: Flow<String?> = context.cloudSyncDataStore.data
        .map { preferences -> preferences[Keys.LastError]?.takeIf { it.isNotBlank() } }

    val connectErrorAt: Flow<Long?> = context.cloudSyncDataStore.data
        .map { preferences -> preferences[Keys.LastErrorAt] }

    suspend fun recordConnectError(message: String, atMillis: Long = System.currentTimeMillis()) {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[Keys.LastError] = message
            preferences[Keys.LastErrorAt] = atMillis
        }
    }

    suspend fun clearConnectError() {
        context.cloudSyncDataStore.edit { preferences ->
            preferences.remove(Keys.LastError)
            preferences.remove(Keys.LastErrorAt)
        }
    }

    val autoBackup: Flow<CloudAutoBackupSettings> = context.cloudSyncDataStore.data
        .map { preferences -> preferences.toAutoBackupSettings() }

    suspend fun currentConnectedEmail(): String? = connectedEmail.first()

    suspend fun currentAutoBackup(): CloudAutoBackupSettings = autoBackup.first()

    suspend fun setConnectedEmail(email: String?) {
        context.cloudSyncDataStore.edit { preferences ->
            if (email.isNullOrBlank()) {
                preferences.remove(Keys.ConnectedEmail)
            } else {
                preferences[Keys.ConnectedEmail] = email
            }
        }
    }

    suspend fun updateAutoBackup(
        enabled: Boolean? = null,
        intervalHours: Int? = null,
        wifiOnly: Boolean? = null,
        retentionCount: Int? = null,
        mode: BackupExportMode? = null,
    ) {
        context.cloudSyncDataStore.edit { preferences ->
            enabled?.let { preferences[Keys.AutoEnabled] = it }
            intervalHours?.let { preferences[Keys.AutoIntervalHours] = it.coerceAtLeast(1) }
            wifiOnly?.let { preferences[Keys.AutoWifiOnly] = it }
            retentionCount?.let { preferences[Keys.AutoRetention] = it.coerceAtLeast(1) }
            mode?.let { preferences[Keys.AutoMode] = it.name }
        }
    }

    suspend fun recordAttempt(atMillis: Long, message: String?) {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[Keys.LastAttempt] = atMillis
            if (message.isNullOrBlank()) {
                preferences.remove(Keys.LastMessage)
            } else {
                preferences[Keys.LastMessage] = message
            }
        }
    }

    suspend fun recordSuccess(atMillis: Long, signature: Long?, message: String?) {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[Keys.LastSuccess] = atMillis
            preferences[Keys.LastAttempt] = atMillis
            signature?.let { preferences[Keys.LastSignature] = it }
            if (message.isNullOrBlank()) {
                preferences.remove(Keys.LastMessage)
            } else {
                preferences[Keys.LastMessage] = message
            }
        }
    }

    suspend fun recordFailure(atMillis: Long, message: String) {
        context.cloudSyncDataStore.edit { preferences ->
            preferences[Keys.LastAttempt] = atMillis
            preferences[Keys.LastMessage] = message
        }
    }

    suspend fun clearConnection() {
        context.cloudSyncDataStore.edit { preferences ->
            preferences.remove(Keys.ConnectedEmail)
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toAutoBackupSettings(): CloudAutoBackupSettings =
        CloudAutoBackupSettings(
            enabled = this[Keys.AutoEnabled] == true,
            intervalHours = (this[Keys.AutoIntervalHours] ?: DEFAULT_AUTO_BACKUP_INTERVAL_HOURS)
                .takeIf { it > 0 } ?: DEFAULT_AUTO_BACKUP_INTERVAL_HOURS,
            wifiOnly = this[Keys.AutoWifiOnly] ?: true,
            retentionCount = (this[Keys.AutoRetention] ?: DEFAULT_CLOUD_BACKUP_RETENTION).coerceAtLeast(1),
            mode = runCatching {
                BackupExportMode.valueOf(this[Keys.AutoMode] ?: BackupExportMode.Full.name)
            }.getOrDefault(BackupExportMode.Full),
            lastSuccessMillis = this[Keys.LastSuccess],
            lastAttemptMillis = this[Keys.LastAttempt],
            lastSignature = this[Keys.LastSignature],
            lastMessage = this[Keys.LastMessage],
        )
}
