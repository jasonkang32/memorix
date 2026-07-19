package com.mebonsoft.memorix.core.database.dao

import androidx.room.Room
import com.mebonsoft.memorix.core.database.MemorixDatabase
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaTagCrossRef
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.database.entity.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TagDaoTest {
    private lateinit var database: MemorixDatabase
    private lateinit var tagDao: TagDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MemorixDatabase::class.java,
        ).allowMainThreadQueries().build()
        tagDao = database.tagDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeTopTags_countsDistinctRegistrationGroupsInsteadOfMediaRows() = runTest {
        val tagId = tagDao.insert(TagEntity(key = "overseas-trip", label = "해외출장", colorHex = "#000000", iconName = "tag"))
        val firstBatchIds = database.mediaDao().insertAll(
            (1..6).map { index -> mediaItem(id = index.toLong(), batchGroupId = "batch-a") },
        )
        val secondBatchIds = database.mediaDao().insertAll(
            (7..11).map { index -> mediaItem(id = index.toLong(), batchGroupId = "batch-b") },
        )
        tagDao.replaceMediaTags((firstBatchIds + secondBatchIds).map { mediaId -> MediaTagCrossRef(mediaId, tagId) })

        val tags = tagDao.observeTopTags(limit = 10).first()

        assertEquals(listOf("해외출장"), tags.map { it.label })
        assertEquals(2, tags.single().usageCount)
    }

    @Test
    fun observeTopTagsForSpace_countsDistinctRegistrationGroupsInThatSpace() = runTest {
        val tagId = tagDao.insert(TagEntity(key = "overseas-trip", label = "해외출장", colorHex = "#000000", iconName = "tag"))
        val workBatchIds = database.mediaDao().insertAll(
            (1..6).map { index -> mediaItem(id = index.toLong(), batchGroupId = "work-batch", space = MediaSpace.WORK) },
        )
        val personalBatchIds = database.mediaDao().insertAll(
            (7..11).map { index -> mediaItem(id = index.toLong(), batchGroupId = "personal-batch", space = MediaSpace.PERSONAL) },
        )
        tagDao.replaceMediaTags((workBatchIds + personalBatchIds).map { mediaId -> MediaTagCrossRef(mediaId, tagId) })

        val workTags = tagDao.observeTopTagsForSpace(MediaSpace.WORK, limit = 10).first()
        val personalTags = tagDao.observeTopTagsForSpace(MediaSpace.PERSONAL, limit = 10).first()

        assertEquals(1, workTags.single().usageCount)
        assertEquals(1, personalTags.single().usageCount)
    }
}

private fun mediaItem(
    id: Long,
    batchGroupId: String,
    space: MediaSpace = MediaSpace.WORK,
): MediaItemEntity = MediaItemEntity(
    id = id,
    mediaType = MediaType.PHOTO,
    filePath = "/tmp/$id.jpg",
    title = "photo-$id",
    takenAt = id,
    createdAt = id,
    space = space,
    batchGroupId = batchGroupId,
)
