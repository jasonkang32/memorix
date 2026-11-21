package com.mebonsoft.memorix.core.security

import android.content.Context
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val SECRET_MEDIA_KEY_DIR = "memorix/security"
private const val SECRET_MEDIA_KEY_FILE = "secret-media.key"
private const val SECRET_FILE_MAGIC = "MRXSECRET1"
private const val AES_KEY_BYTES = 32
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val PLAIN_VIEW_CACHE_DIR = "secret-view"

/**
 * 잠금·비밀 항목 파일(`.mrxsecret`)의 암복호화.
 *
 * 화면 표시(Coil), 영상 재생, 공유·내보내기가 같은 키로 복호화할 수 있도록 [SecretMediaStorageManager]에서 분리했다.
 */
object SecretMediaCrypto {
    fun encrypt(context: Context, plain: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec(context), GCMParameterSpec(GCM_TAG_BITS, iv))
        return SECRET_FILE_MAGIC.toByteArray(Charsets.UTF_8) + iv + cipher.doFinal(plain)
    }

    fun decrypt(context: Context, encrypted: ByteArray): ByteArray {
        val magic = SECRET_FILE_MAGIC.toByteArray(Charsets.UTF_8)
        require(encrypted.size > magic.size + GCM_IV_BYTES && encrypted.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Memorix 비밀 파일 형식이 아닙니다."
        }
        val iv = encrypted.copyOfRange(magic.size, magic.size + GCM_IV_BYTES)
        val payload = encrypted.copyOfRange(magic.size + GCM_IV_BYTES, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec(context), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(payload)
    }

    /**
     * 재생·공유처럼 실제 파일이 필요한 곳에 쓸 평문 파일.
     * 암호화되지 않은 경로는 그대로 돌려주고, 암호화된 경로는 캐시에 복호화한다(다음 앱 시작 때 [clearPlainViewCache]로 지운다).
     */
    fun plainFile(context: Context, path: String): File {
        val source = File(path)
        if (!SecretMediaPathSupport.isEncryptedPath(path) || !source.isFile) return source
        val dir = File(context.cacheDir, PLAIN_VIEW_CACHE_DIR).apply { mkdirs() }
        // 원본 파일명은 공유·저장 시 보이므로 유지하고, 같은 이름 충돌을 피하려 상위 폴더명(uuid)을 붙인다.
        val name = File(SecretMediaPathSupport.decryptedPath(path)).name
        val target = File(dir, "${source.parentFile?.name.orEmpty()}_$name")
        if (!target.isFile || target.lastModified() < source.lastModified()) {
            target.writeBytes(decrypt(context, source.readBytes()))
        }
        return target
    }

    fun clearPlainViewCache(context: Context) {
        File(context.cacheDir, PLAIN_VIEW_CACHE_DIR).deleteRecursively()
    }

    private fun secretKeySpec(context: Context): SecretKeySpec = SecretKeySpec(loadOrCreatePortableSecretKey(context), "AES")

    private fun loadOrCreatePortableSecretKey(context: Context): ByteArray {
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
