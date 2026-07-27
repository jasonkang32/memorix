package com.mebonsoft.memorix.app

import android.app.Application
import com.mebonsoft.memorix.core.security.SecretMediaMigrationRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MemorixApplication : Application() {
    @Inject lateinit var secretMediaMigrationRunner: SecretMediaMigrationRunner

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { secretMediaMigrationRunner.hardenExistingSecretFiles() }
        }
    }
}
