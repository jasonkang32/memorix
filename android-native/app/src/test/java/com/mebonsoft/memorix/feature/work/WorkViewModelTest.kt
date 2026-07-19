package com.mebonsoft.memorix.feature.work

import android.net.Uri
import org.robolectric.RuntimeEnvironment
import com.mebonsoft.memorix.core.auth.AuthRepository
import com.mebonsoft.memorix.core.database.dao.MediaTagAssignment
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaTagCrossRef
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.database.entity.TagEntity
import com.mebonsoft.memorix.core.media.ImportDateRange
import com.mebonsoft.memorix.core.media.ImportPreview
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

        val viewModel = WorkViewModel(repository, authRepository(), FakeTagDao())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertTrue(state.items.all { it.space == MediaSpace.WORK })
        assertEquals(2, state.filteredItems.size)
    }

    @Test
    fun `updateQuery filters work items by note`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(
                mediaItem(id = 1, title = "현장 사진", note = "서울 가산"),
                mediaItem(id = 2, title = "출장 문서", note = "부산 보고서", mediaType = MediaType.DOCUMENT),
            ),
        )

        val viewModel = WorkViewModel(repository, authRepository(), FakeTagDao())
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

        val viewModel = WorkViewModel(repository, authRepository(), FakeTagDao())
        advanceUntilIdle()

        viewModel.updateMediaType(MediaType.VIDEO)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MediaType.VIDEO, state.selectedMediaType)
        assertEquals(listOf(2L), state.filteredItems.map { it.id })
        assertEquals("영상 1개", state.summary)
    }

    @Test
    fun `uiState exposes tags by media id for timeline groups`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(mediaItem(id = 1, title = "현장 사진")),
        )

        val viewModel = WorkViewModel(
            repository,
            authRepository(),
            FakeTagDao(assignments = listOf(MediaTagAssignment(mediaId = 1, tagId = 10, label = "계약"))),
        )
        advanceUntilIdle()

        assertEquals(listOf("계약"), viewModel.uiState.value.tagsByMediaId[1])
    }

    @Test
    fun `updateQuery filters work items by tag label`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(
                mediaItem(id = 1, title = "현장 사진", note = "서울"),
                mediaItem(id = 2, title = "견적 사진", note = "부산"),
            ),
        )
        val viewModel = WorkViewModel(
            repository,
            authRepository(),
            FakeTagDao(assignments = listOf(MediaTagAssignment(mediaId = 2, tagId = 10, label = "계약"))),
        )
        advanceUntilIdle()

        viewModel.updateQuery("계약")
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.uiState.value.filteredItems.map { it.id })
    }


    @Test
    fun `updateTagFilter keeps matching work records and toggles off`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(
                mediaItem(id = 1, title = "회의 사진", note = "서울"),
                mediaItem(id = 2, title = "계약 문서", note = "부산", mediaType = MediaType.DOCUMENT),
            ),
        )
        val viewModel = WorkViewModel(
            repository,
            authRepository(),
            FakeTagDao(
                assignments = listOf(
                    MediaTagAssignment(mediaId = 1, tagId = 10, label = "회의"),
                    MediaTagAssignment(mediaId = 2, tagId = 11, label = "계약"),
                ),
                topTags = listOf(tagUsage(label = "회의", usageCount = 2), tagUsage(label = "계약", usageCount = 1)),
            ),
        )
        advanceUntilIdle()

        viewModel.updateTagFilter("회의")
        advanceUntilIdle()

        assertEquals("회의", viewModel.uiState.value.selectedTagLabel)
        assertEquals(listOf(1L), viewModel.uiState.value.filteredItems.map { it.id })
        assertEquals(listOf("회의", "계약"), viewModel.uiState.value.topTags.map { it.label })

        viewModel.updateTagFilter("회의")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedTagLabel)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.filteredItems.map { it.id })
    }

    @Test
    fun `secret work items are hidden until unlocked`() = runTest(dispatcher) {
        val repository = FakeMediaRepository(
            workItems = listOf(
                mediaItem(id = 1, title = "일반", note = "공개"),
                mediaItem(id = 2, title = "비밀", note = "숨김", isSecret = true),
            ),
        )
        val viewModel = WorkViewModel(repository, authRepository(), FakeTagDao())
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.uiState.value.filteredItems.map { it.id })

        viewModel.unlockSecretsByBiometric()
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.filteredItems.map { it.id })
    }
}

private fun authRepository(): AuthRepository = AuthRepository(RuntimeEnvironment.getApplication())

private class FakeTagDao(
    private val assignments: List<MediaTagAssignment> = emptyList(),
    private val topTags: List<TagUsageSummary> = emptyList(),
) : TagDao {
    override fun observeTags(): Flow<List<TagEntity>> = flowOf(emptyList())

    override fun observeManagedTags(): Flow<List<com.mebonsoft.memorix.core.database.dao.ManagedTagSummary>> = flowOf(emptyList())

    override fun observeTagsForMedia(mediaId: Long): Flow<List<TagEntity>> = flowOf(emptyList())

    override fun observeMediaTagAssignments(): Flow<List<MediaTagAssignment>> = flowOf(assignments)

    override fun observeTopTags(limit: Int): Flow<List<TagUsageSummary>> = flowOf(emptyList())

    override fun observeTopTagsForSpace(space: MediaSpace, limit: Int): Flow<List<TagUsageSummary>> = flowOf(topTags.take(limit))

    override suspend fun insert(tag: TagEntity): Long = tag.id

    override suspend fun replaceMediaTags(crossRefs: List<MediaTagCrossRef>) = Unit

    override suspend fun clearMediaTags(mediaId: Long) = Unit
    override suspend fun clearTagAssignments(tagId: Long) = Unit
    override suspend fun deleteTagById(tagId: Long) = Unit
    override suspend fun deleteManagedTag(tagId: Long) = Unit
}

private fun tagUsage(label: String, usageCount: Int): TagUsageSummary = TagUsageSummary(
    id = label.hashCode().toLong(),
    key = label,
    label = label,
    colorHex = "#000000",
    iconName = "tag",
    usageCount = usageCount,
)

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
    isSecret: Boolean = false,
): MediaItemEntity = MediaItemEntity(
    id = id,
    mediaType = mediaType,
    filePath = "/tmp/$id.jpg",
    title = title,
    note = note,
    takenAt = id,
    createdAt = id,
    space = space,
    isSecret = isSecret,
)
