package com.mebonsoft.memorix.core.security

import android.content.Context
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 잠금 또는 비밀이면 보호 대상이며, 보호 대상 파일은 앱 내부에서 암호화한다. */
val MediaItemEntity.isProtected: Boolean get() = isSecret || isLocked

@Singleton
class SecretMediaStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun applySecretState(
        current: MediaItemEntity?,
        requested: MediaItemEntity,
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        when {
            current?.isProtected != true && requested.isProtected -> encryptItemFiles(requested)
            current?.isProtected == true && !requested.isProtected -> decryptItemFiles(requested)
            else -> requested
        }
    }

    suspend fun hardenSecretItem(item: MediaItemEntity): MediaItemEntity = withContext(Dispatchers.IO) {
        if (!item.isProtected) item else encryptItemFiles(item)
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
        target.writeBytes(SecretMediaCrypto.encrypt(context, source.readBytes()))
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
        target.writeBytes(SecretMediaCrypto.decrypt(context, source.readBytes()))
        if (!source.delete()) {
            target.delete()
            error("비밀 파일을 일반 보관함으로 복원하지 못했습니다.")
        }
        return target.absolutePath
    }
}
