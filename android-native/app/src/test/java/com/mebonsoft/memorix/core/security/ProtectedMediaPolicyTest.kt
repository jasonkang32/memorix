package com.mebonsoft.memorix.core.security

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedMediaPolicyTest {
    @Test
    fun protection_roundTripsThroughEntityFlags() {
        MediaProtection.entries.forEach { protection ->
            val item = item().withProtection(protection)
            assertEquals(protection, MediaProtection.of(item))
        }
        assertFalse(item().withProtection(MediaProtection.None).isProtected)
        assertTrue(item().withProtection(MediaProtection.Locked).isProtected)
        assertTrue(item().withProtection(MediaProtection.Secret).isProtected)
    }

    @Test
    fun listPolicy_hidesSecretAndKeepsLockedOutOfSearch() {
        val normal = item()
        val locked = item().withProtection(MediaProtection.Locked)
        val secret = item().withProtection(MediaProtection.Secret)

        assertTrue(ProtectedListPolicy.isListed(normal, query = "", matchesQuery = true))
        assertTrue(ProtectedListPolicy.isListed(locked, query = "", matchesQuery = true))
        assertFalse(ProtectedListPolicy.isListed(secret, query = "", matchesQuery = true))

        assertTrue(ProtectedListPolicy.isListed(normal, query = "회의", matchesQuery = true))
        assertFalse(ProtectedListPolicy.isListed(normal, query = "회의", matchesQuery = false))
        assertFalse(ProtectedListPolicy.isListed(locked, query = "회의", matchesQuery = true))
    }

    private fun item() = MediaItemEntity(mediaType = MediaType.PHOTO, filePath = "/tmp/a.jpg", takenAt = 0L, createdAt = 0L)
}
