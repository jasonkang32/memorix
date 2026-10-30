package com.mebonsoft.memorix.core.cloud

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface DriveAuthorization {
    data class Granted(val accessToken: String) : DriveAuthorization

    data class ConsentRequired(val intentSender: IntentSender) : DriveAuthorization

    data class Failed(val message: String, val statusCode: Int? = null) : DriveAuthorization
}

/**
 * Google Drive 접근 권한을 AuthorizationClient로 처리한다.
 *
 * 기존 GoogleSignIn(레거시) 흐름을 대체한다. 인증(로그인)과 승인(Scope)을 분리하는 Google 권장 방식이며,
 * 한 번 승인된 뒤에는 사용자 조작 없이 조용히 액세스 토큰을 재발급받을 수 있어 자동 백업에 그대로 쓸 수 있다.
 */
@Singleton
class DriveTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val scopes: List<Scope> = listOf(Scope(DriveScopes.DRIVE_APPDATA))

    private val client get() = Identity.getAuthorizationClient(context)

    private fun request(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(scopes)
        .build()

    /**
     * 승인된 토큰을 가져온다. 사용자 동의가 필요하면 [DriveAuthorization.ConsentRequired]를 돌려준다.
     */
    suspend fun authorize(): DriveAuthorization {
        return try {
            val result = client.authorize(request()).await()
            Log.i(TAG, "authorize: token=${if (result.accessToken != null) "ok" else "null"}, resolution=${result.hasResolution()}, scopes=${result.grantedScopes?.size ?: 0}")
            toAuthorization(result)
        } catch (error: ApiException) {
            Log.w(TAG, "authorize failed: status=${error.statusCode} msg=${error.localizedMessage}", error)
            DriveAuthorization.Failed(
                message = DriveCloudSyncSupport.mapAuthorizationError(error.statusCode, error.localizedMessage),
                statusCode = error.statusCode,
            )
        } catch (error: Exception) {
            Log.w(TAG, "authorize failed (non-api)", error)
            DriveAuthorization.Failed(
                message = DriveCloudSyncSupport.mapAuthorizationError(null, error.localizedMessage),
            )
        }
    }

    /**
     * 사용자 조작 없이 토큰을 얻는다(백그라운드 자동 백업용). 동의가 필요하면 null.
     */
    suspend fun silentAccessToken(): String? = when (val authorization = authorize()) {
        is DriveAuthorization.Granted -> authorization.accessToken
        is DriveAuthorization.ConsentRequired -> null
        is DriveAuthorization.Failed -> throw DriveSyncException(
            message = authorization.message,
            statusCode = authorization.statusCode,
            transient = authorization.statusCode?.let(DriveCloudSyncSupport::isTransientAuthorizationStatus) == true,
        )
    }

    /**
     * 동의 화면 결과 Intent를 승인 결과로 변환한다.
     */
    fun resultFromIntent(data: Intent?): AuthorizationResult {
        requireNotNull(data) { "Google 승인 결과가 비어 있습니다. 다시 시도해 주세요." }
        val result = client.getAuthorizationResultFromIntent(data)
        Log.i(TAG, "consent result: token=${if (result.accessToken != null) "ok" else "null"}, resolution=${result.hasResolution()}")
        return result
    }

    fun toAuthorization(result: AuthorizationResult): DriveAuthorization {
        val token = result.accessToken
        if (!token.isNullOrBlank()) return DriveAuthorization.Granted(token)
        return if (result.hasResolution()) {
            DriveAuthorization.ConsentRequired(
                requireNotNull(result.pendingIntent) { "Google 승인 화면을 열 수 없습니다." }.intentSender,
            )
        } else {
            DriveAuthorization.Failed(DriveCloudSyncSupport.mapAuthorizationError(null, null))
        }
    }

    /** 401을 받았을 때 로컬 토큰 캐시에서 해당 토큰을 제거한다. */
    suspend fun invalidateToken(token: String) {
        runCatching {
            client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        }
    }

    /** 연결 해제 시 앱에 부여된 Drive 권한을 취소한다. 실패해도 로컬 상태 정리는 계속 진행한다. */
    suspend fun revoke(): Boolean = runCatching {
        client.revokeAccess(RevokeAccessRequest.builder().setScopes(scopes).build()).await()
        true
    }.getOrDefault(false)

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
}

/**
 * Drive 작업 실패. [transient]가 true면 네트워크/서버 일시 오류라 자동 백업이 backoff 후 재시도한다.
 */
class DriveSyncException(
    message: String,
    val statusCode: Int? = null,
    val transient: Boolean = false,
) : IllegalStateException(message)

private const val TAG = "MemorixDrive"
