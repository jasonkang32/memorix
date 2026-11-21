package com.mebonsoft.memorix.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 잠금·비밀 항목 인증 상태. 한 번 인증하면 앱을 쓰는 동안 유지한다.
 *
 * 앱이 백그라운드에 [RELOCK_AFTER_BACKGROUND_MILLIS] 이상 있으면 다시 잠근다. 바로 잠그지 않는 이유는
 * 공유 대상 선택·파일 저장 위치 선택처럼 잠깐 다른 화면에 다녀오는 동작마다 재인증을 요구하지 않기 위해서다.
 */
@Singleton
class ProtectedMediaSession @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val unlockedState = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = unlockedState.asStateFlow()

    fun unlock() {
        unlockedState.value = true
    }

    fun lock() {
        unlockedState.value = false
    }

    private var startedActivities = 0
    private var backgroundSince: Long? = null

    fun onActivityStarted(nowMillis: Long) {
        startedActivities += 1
        val since = backgroundSince
        backgroundSince = null
        if (since != null && nowMillis - since >= RELOCK_AFTER_BACKGROUND_MILLIS) lock()
    }

    fun onActivityStopped(nowMillis: Long) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) backgroundSince = nowMillis
    }

    /** 공유 중인 파일이 있을 수 있어 실행 중에는 지우지 않고, 앱 시작 시 이전 실행의 복호화 캐시를 지운다. */
    fun clearPlainViewCacheOnStart() {
        runCatching { SecretMediaCrypto.clearPlainViewCache(context) }
    }

    companion object {
        const val RELOCK_AFTER_BACKGROUND_MILLIS = 60_000L
    }
}
