package com.mebonsoft.memorix.core.security

import android.content.Context
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SECRET_MEDIA_KEY_DIR = "memorix/security"
private const val SECRET_MEDIA_KEY_FILE = "secret-media.key"
private const val SECRET_FILE_MAGIC = "MRXSECRET1"
private const val AES_KEY_BYTES = 32
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128

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
        target.writeBytes(encryptBytes(source.readBytes()))
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
        target.writeBytes(decryptBytes(source.readBytes()))
        if (!source.delete()) {
            target.delete()
            error("비밀 파일을 일반 보관함으로 복원하지 못했습니다.")
        }
        return target.absolutePath
    }

    private fun encryptBytes(plain: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)
        return SECRET_FILE_MAGIC.toByteArray(Charsets.UTF_8) + iv + encrypted
    }

    private fun decryptBytes(encrypted: ByteArray): ByteArray {
        val magic = SECRET_FILE_MAGIC.toByteArray(Charsets.UTF_8)
        require(encrypted.size > magic.size + GCM_IV_BYTES && encrypted.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Memorix 비밀 파일 형식이 아닙니다."
        }
        val iv = encrypted.copyOfRange(magic.size, magic.size + GCM_IV_BYTES)
        val payload = encrypted.copyOfRange(magic.size + GCM_IV_BYTES, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(payload)
    }

    private fun secretKeySpec(): SecretKeySpec = SecretKeySpec(loadOrCreatePortableSecretKey(), "AES")

    private fun loadOrCreatePortableSecretKey(): ByteArray {
        val keyFile = File(context.filesDir, "$SECRET_MEDIA_KEY_DIR/$SECRET_MEDIA_KEY_FILE")
        if (keyFile.exists()) {
            val bytes = keyFile.readBytes()
            require(bytes.size == AES_KEY_BYTES) { "Memorix 비밀 보관함 키가 올바르지 않습니다." }
            return bytes
        }
        keyFile.parentFile?.mkdirs()
        val bytes = ByteArray(AES_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        keyFile.writeBytes(bytes)
        return bytes
    }
}
