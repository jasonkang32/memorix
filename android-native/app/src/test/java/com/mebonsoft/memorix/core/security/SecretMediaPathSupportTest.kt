package com.mebonsoft.memorix.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretMediaPathSupportTest {
    @Test
    fun encryptedPath_appendsSecretExtensionOnce() {
        val plain = "/files/memorix/originals/2026/07/photo.jpg"

        val encrypted = SecretMediaPathSupport.encryptedPath(plain)

        assertEquals("$plain.mrxsecret", encrypted)
        assertEquals(encrypted, SecretMediaPathSupport.encryptedPath(encrypted))
        assertTrue(SecretMediaPathSupport.isEncryptedPath(encrypted))
        assertFalse(SecretMediaPathSupport.isEncryptedPath(plain))
    }

    @Test
    fun decryptedPath_removesSecretExtensionOnlyWhenPresent() {
        val plain = "/files/memorix/originals/2026/07/photo.jpg"
        val encrypted = "$plain.mrxsecret"

        assertEquals(plain, SecretMediaPathSupport.decryptedPath(encrypted))
        assertEquals(plain, SecretMediaPathSupport.decryptedPath(plain))
    }
}
