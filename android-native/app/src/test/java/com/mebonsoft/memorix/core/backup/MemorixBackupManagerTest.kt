package com.mebonsoft.memorix.core.backup

import androidx.room.Room
import com.mebonsoft.memorix.core.database.MemorixDatabase
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MemorixBackupManagerTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: MemorixDatabase
    private lateinit var manager: MemorixBackupManager

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME_FOR_TEST)
        database = Room.databaseBuilder(context, MemorixDatabase::class.java, DATABASE_NAME_FOR_TEST)
            .allowMainThreadQueries()
            .build()
        manager = MemorixBackupManager(context, database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME_FOR_TEST)
        context.filesDir.resolve("memorix").deleteRecursively()
        context.cacheDir.resolve("memorix-camera").deleteRecursively()
    }

    @Test
    fun resetAllData_clearsOpenDatabaseAndManagedFilesWithoutRestart() = runTest {
        val mediaRoot = context.filesDir.resolve("memorix/originals")
        val cameraRoot = context.cacheDir.resolve("memorix-camera")
        val mediaFile = mediaRoot.resolve("sample.jpg")
        val cameraFile = cameraRoot.resolve("pending.jpg")
        mediaRoot.mkdirs()
        cameraRoot.mkdirs()
        mediaFile.writeText("image-bytes")
        cameraFile.writeText("camera-bytes")

        database.mediaDao().insert(
            MediaItemEntity(
                mediaType = MediaType.PHOTO,
                filePath = mediaFile.absolutePath,
                title = "남아 있으면 안 되는 Home 항목",
                takenAt = 1_000L,
                createdAt = 1_000L,
                mimeType = "image/jpeg",
            )
        )
        assertEquals(1, database.mediaDao().listLibrary().size)
        assertTrue(mediaFile.exists())
        assertTrue(cameraFile.exists())

        val usage = manager.resetAllData()

        assertEquals(emptyList<MediaItemEntity>(), database.mediaDao().listLibrary())
        assertFalse(mediaFile.exists())
        assertFalse(cameraFile.exists())
        assertEquals(0L, usage.mediaBytes)
    }

    private companion object {
        const val DATABASE_NAME_FOR_TEST = "memorix-native.db"
    }
}
