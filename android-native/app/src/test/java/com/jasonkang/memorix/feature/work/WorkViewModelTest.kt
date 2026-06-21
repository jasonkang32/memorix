package com.jasonkang.memorix.feature.work

import android.net.Uri
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.database.entity.MediaType
import com.jasonkang.memorix.core.media.ImportDateRange
import com.jasonkang.memorix.core.media.ImportPreview
import com.jasonkang.memorix.data.repository.MediaRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WorkViewModelTest {
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
    fun `uiState exposes work items only`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(
                mediaItem(id = 1, title = "현장 사진", mediaType = MediaType.PHOTO),
                mediaItem(id = 2, title = "작업 영상", mediaType = MediaType.VIDEO),
            ),
            personalItems = listOf(
                mediaItem(id = 3, title = "가족 사진", mediaType = MediaType.PHOTO, space = MediaSpace.PERSONAL),
            ),
        )

        val viewModel = WorkViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertTrue(state.items.all { it.space == MediaSpace.WORK })
        assertEquals(2, state.filteredItems.size)
    }

    @Test
    fun `updateQuery filters work items by title and note`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(
                mediaItem(id = 1, title = "현장 사진", note = "서울 가산"),
                mediaItem(id = 2, title = "출장 문서", note = "부산 보고서", mediaType = MediaType.DOCUMENT),
            ),
        )

        val viewModel = WorkViewModel(repository)
        advanceUntilIdle()

        viewModel.updateQuery("부산")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("부산", state.query)
        assertEquals(listOf(2L), state.filteredItems.map { it.id })
    }

    @Test
    fun `updateMediaType keeps only selected type`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(
                mediaItem(id = 1, title = "현장 사진", mediaType = MediaType.PHOTO),
                mediaItem(id = 2, title = "작업 영상", mediaType = MediaType.VIDEO),
            ),
        )

        val viewModel = WorkViewModel(repository)
        advanceUntilIdle()

        viewModel.updateMediaType(MediaType.VIDEO)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MediaType.VIDEO, state.selectedMediaType)
        assertEquals(listOf(2L), state.filteredItems.map { it.id })
        assertEquals("영상 1개", state.summary)
    }
}

private class FakeMediaRepository(
    private val workItems: List<MediaItemEntity> = emptyList(),
    private val personalItems: List<MediaItemEntity> = emptyList(),
) : MediaRepository {
    override fun observeLibrary(): Flow<List<MediaItemEntity>> = flowOf(workItems + personalItems)

    override fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>> = flowOf(
        when (space) {
            MediaSpace.WORK -> workItems
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

private fun mediaItem(
    id: Long,
    title: String,
    note: String = "",
    mediaType: MediaType = MediaType.PHOTO,
    space: MediaSpace = MediaSpace.WORK,
): MediaItemEntity = MediaItemEntity(
    id = id,
    mediaType = mediaType,
    filePath = "/tmp/$id.jpg",
    title = title,
    note = note,
    takenAt = id,
    createdAt = id,
    space = space,
)
