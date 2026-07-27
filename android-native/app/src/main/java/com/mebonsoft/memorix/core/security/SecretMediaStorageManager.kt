package com.mebonsoft.memorix.core.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SecretMediaStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun applySecretState(
        current: MediaItemEntity?,
        requested: MediaItemEntity,
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        when {
            current?.isSecret != true && requested.isSecret -> encryptItemFiles(requested)
            current?.isSecret == true && !requested.isSecret -> decryptItemFiles(requested)
            else -> requested
        }
    }

    suspend fun hardenSecretItem(item: MediaItemEntity): MediaItemEntity = withContext(Dispatchers.IO) {
        if (!item.isSecret) item else encryptItemFiles(item)
    }

    private fun encryptItemFiles(item: MediaItemEntity): MediaItemEntity = item.copy(
        filePath = encryptFilePath(item.filePath),
        thumbPath = item.thumbPath?.let(::encryptFilePath),
    )

    private fun decryptItemFiles(item: MediaItemEntity): MediaItemEntity = item.copy(
        filePath = decryptFilePath(item.filePath),
        thumbPath = item.thumbPath?.let(::decryptFilePath),
    )

    private fun encryptFilePath(path: String): String {
        if (SecretMediaPathSupport.isEncryptedPath(path)) return path
        val source = File(path)
        if (!source.exists() || !source.isFile) return path
        val target = File(SecretMediaPathSupport.encryptedPath(path))
        if (target.exists()) return target.absolutePath
        target.parentFile?.mkdirs()
        encryptedFile(target).openFileOutput().use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        }
        if (!source.delete()) {
            target.delete()
            error("원본 파일을 비밀 보관함으로 이동하지 못했습니다.")
        }
        return target.absolutePath
    }

    private fun decryptFilePath(path: String): String {
        if (!SecretMediaPathSupport.isEncryptedPath(path)) return path
        val source = File(path)
        if (!source.exists() || !source.isFile) return SecretMediaPathSupport.decryptedPath(path)
        val target = File(SecretMediaPathSupport.decryptedPath(path))
        if (target.exists()) return target.absolutePath
        target.parentFile?.mkdirs()
        encryptedFile(source).openFileInput().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (!source.delete()) {
            target.delete()
            error("비밀 파일을 일반 보관함으로 복원하지 못했습니다.")
        }
        return target.absolutePath
    }

    private fun encryptedFile(file: File): EncryptedFile {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }
}
