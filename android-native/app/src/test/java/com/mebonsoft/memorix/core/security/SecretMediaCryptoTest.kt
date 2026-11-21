package com.mebonsoft.memorix.core.security

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SecretMediaCryptoTest {
    @Test
    fun plainFile_decryptsEncryptedMediaForPlaybackAndSharing() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.filesDir, "memorix/originals/uuid-1").apply { mkdirs() }
        val plainBytes = "locked-photo".toByteArray()
        val encrypted = File(dir, "photo.jpg.mrxsecret").apply { writeBytes(SecretMediaCrypto.encrypt(context, plainBytes)) }

        val plain = SecretMediaCrypto.plainFile(context, encrypted.absolutePath)

        assertEquals("uuid-1_photo.jpg", plain.name)
        assertArrayEquals(plainBytes, plain.readBytes())

        SecretMediaCrypto.clearPlainViewCache(context)
        assertFalse(plain.exists())
    }

    @Test
    fun plainFile_returnsUnencryptedPathAsIs() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "memorix/originals/plain.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        assertEquals(file, SecretMediaCrypto.plainFile(context, file.absolutePath))
    }
}
