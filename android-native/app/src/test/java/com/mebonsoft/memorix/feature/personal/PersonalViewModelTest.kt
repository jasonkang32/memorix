package com.mebonsoft.memorix.feature.personal

import android.net.Uri
import com.mebonsoft.memorix.core.database.dao.MediaTagAssignment
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.AlbumEntity
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaTagCrossRef
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.database.entity.TagEntity
import com.mebonsoft.memorix.core.media.ImportDateRange
import com.mebonsoft.memorix.core.media.ImportPreview
import com.mebonsoft.memorix.data.repository.AlbumRepository
import com.mebonsoft.memorix.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState exposes personal albums and items`() = runTest(dispatcher) {
        val viewModel = PersonalViewModel(
            mediaRepository = FakeMediaRepository(
                personalItems = listOf(
                    mediaItem(id = 11, title = "가족 사진"),
                    mediaItem(id = 12, title = "반려견 영상", mediaType = MediaType.VIDEO),
                ),
            ),
            albumRepository = FakeAlbumRepository(
                albums = listOf(
                    albumSummary(id = 1, title = "여름 휴가"),
                    albumSummary(id = 2, title = "우리집"),
                ),
            ),
            tagDao = FakeTagDao(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.albums.size)
        assertEquals(2, state.items.size)
        assertEquals("앨범 2개 · 미디어 2개", state.summary)
    }

    @Test
    fun `toggleAlbumGrid flips album mode flag`() = runTest(dispatcher) {
        val viewModel = PersonalViewModel(
            mediaRepository = FakeMediaRepository(),
            albumRepository = FakeAlbumRepository(),
            tagDao = FakeTagDao(),
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAlbumGridMode)

        viewModel.toggleAlbumGrid()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAlbumGridMode)
    }

    @Test
    fun `updateQuery filters personal items`() = runTest(dispatcher) {
        val viewModel = PersonalViewModel(
            mediaRepository = FakeMediaRepository(
                personalItems = listOf(
                    mediaItem(id = 11, title = "가족 사진"),
                    mediaItem(id = 12, title = "여행 메모", note = "제주도"),
                ),
            ),
            albumRepository = FakeAlbumRepository(),
            tagDao = FakeTagDao(),
        )
        advanceUntilIdle()

        viewModel.updateQuery("제주")
        advanceUntilIdle()

        assertEquals(listOf(12L), viewModel.uiState.value.filteredItems.map { it.id })
    }
}

private class FakeMediaRepository(
    private val personalItems: List<MediaItemEntity> = emptyList(),
) : MediaRepository {
    override fun observeLibrary(): Flow<List<MediaItemEntity>> = flowOf(personalItems)

    override fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>> = flowOf(
        when (space) {
            MediaSpace.WORK -> emptyList()
            MediaSpace.PERSONAL -> personalItems
        }
    )

    override fun observeAlbum(albumId: Long): Flow<List<MediaItemEntity>> = flowOf(emptyList())

    override fun observeMedia(mediaId: Long): Flow<MediaItemEntity?> = flowOf(null)

    override suspend fun previewImport(uris: List<Uri>, dateRange: ImportDateRange?): ImportPreview = ImportPreview(items = emptyList())

    override suspend fun importMedia(uris: List<Uri>, space: MediaSpace): List<Long> = emptyList()

    override suspend fun updateMedia(item: MediaItemEntity) = Unit

    override suspend fun rebuildSearchIndex(item: MediaItemEntity) = Unit
}

private class FakeTagDao(
    private val assignments: List<MediaTagAssignment> = emptyList(),
) : TagDao {
    override fun observeTags(): Flow<List<TagEntity>> = flowOf(emptyList())
    override fun observeManagedTags(): Flow<List<com.mebonsoft.memorix.core.database.dao.ManagedTagSummary>> = flowOf(emptyList())
    override fun observeTagsForMedia(mediaId: Long): Flow<List<TagEntity>> = flowOf(emptyList())
    override fun observeMediaTagAssignments(): Flow<List<MediaTagAssignment>> = flowOf(assignments)
    override fun observeTopTags(limit: Int) = flowOf(emptyList<com.mebonsoft.memorix.core.database.dao.TagUsageSummary>())
    override fun observeTopTagsForSpace(space: MediaSpace, limit: Int): Flow<List<TagUsageSummary>> = flowOf(emptyList())
    override suspend fun insert(tag: TagEntity): Long = tag.id
    override suspend fun replaceMediaTags(crossRefs: List<MediaTagCrossRef>) = Unit
    override suspend fun clearMediaTags(mediaId: Long) = Unit
    override suspend fun clearTagAssignments(tagId: Long) = Unit
    override suspend fun deleteTagById(tagId: Long) = Unit
    override suspend fun deleteManagedTag(tagId: Long) = Unit
}

private class FakeAlbumRepository(
    private val albums: List<AlbumSummary> = emptyList(),
) : AlbumRepository {
    override fun observeAlbumSummaries(): Flow<List<AlbumSummary>> = flowOf(albums)

    override fun observeAlbum(albumId: Long): Flow<AlbumEntity?> = flowOf(null)

    override suspend fun createAlbum(title: String, memo: String): Long = 1L

    override suspend fun updateAlbum(album: AlbumEntity) = Unit

    override suspend fun deleteAlbum(albumId: Long) = Unit
}

private fun mediaItem(
    id: Long,
    title: String,
    note: String = "",
    mediaType: MediaType = MediaType.PHOTO,
): MediaItemEntity = MediaItemEntity(
    id = id,
    mediaType = mediaType,
    filePath = "/tmp/$id.jpg",
    title = title,
    note = note,
    takenAt = id,
    createdAt = id,
    space = MediaSpace.PERSONAL,
)

private fun albumSummary(
    id: Long,
    title: String,
): AlbumSummary = AlbumSummary(
    id = id,
    title = title,
    memo = "",
    dateStart = null,
    dateEnd = null,
    coverMediaId = null,
    createdAt = id,
    itemCount = 0,
    coverPath = null,
)
