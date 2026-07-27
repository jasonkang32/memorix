package com.mebonsoft.memorix.core.security

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaType
import android.content.ContextWrapper
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SecretMediaStorageManagerTest {
    @Test
    fun secretFilesUsePortableKeyThatSurvivesFileCopyRestore() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val root = File(context.filesDir, "memorix/originals/test").apply { mkdirs() }
        val plain = File(root, "photo.jpg")
        val originalBytes = "portable-secret-photo".toByteArray()
        plain.writeBytes(originalBytes)
        val manager = SecretMediaStorageManager(context)

        val encryptedItem = manager.applySecretState(
            current = null,
            requested = testItem(plain.absolutePath, isSecret = true),
        )

        assertTrue(encryptedItem.filePath.endsWith(".mrxsecret"))
        assertFalse(plain.exists())
        val encryptedFile = File(encryptedItem.filePath)
        assertTrue(encryptedFile.exists())
        val keyFile = File(context.filesDir, "memorix/security/secret-media.key")
        assertTrue(keyFile.exists())

        val restoredRoot = File(context.cacheDir, "restored-phone").apply {
            deleteRecursively()
            mkdirs()
        }
        context.filesDir.copyRecursively(restoredRoot, overwrite = true)
        val restoredEncryptedPath = encryptedItem.filePath.replace(context.filesDir.absolutePath, restoredRoot.absolutePath)
        val restoredManager = SecretMediaStorageManager(context = object : ContextWrapper(context) {
            override fun getFilesDir(): File = restoredRoot
        })

        val decryptedItem = restoredManager.applySecretState(
            current = testItem(restoredEncryptedPath, isSecret = true),
            requested = testItem(restoredEncryptedPath, isSecret = false),
        )

        assertEquals(restoredEncryptedPath.removeSuffix(".mrxsecret"), decryptedItem.filePath)
        assertArrayEquals(originalBytes, File(decryptedItem.filePath).readBytes())
    }

    private fun testItem(path: String, isSecret: Boolean): MediaItemEntity = MediaItemEntity(
        mediaType = MediaType.PHOTO,
        filePath = path,
        takenAt = 0L,
        createdAt = 0L,
        isSecret = isSecret,
    )
}
