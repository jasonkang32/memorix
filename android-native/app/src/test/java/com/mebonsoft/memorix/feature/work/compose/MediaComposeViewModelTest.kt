package com.mebonsoft.memorix.feature.work.compose

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.mebonsoft.memorix.core.database.dao.MediaTagAssignment
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaTagCrossRef
import com.mebonsoft.memorix.core.database.entity.TagEntity
import com.mebonsoft.memorix.core.media.ImportDateRange
import com.mebonsoft.memorix.core.media.ImportPreview
import com.mebonsoft.memorix.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class MediaComposeViewModelTest {
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
    fun `addCustomTag creates and selects typed tag`() = runTest(dispatcher) {
        val tagDao = FakeTagDao()
        val viewModel = MediaComposeViewModel(SavedStateHandle(), FakeMediaRepository(), tagDao)
        advanceUntilIdle()

        viewModel.updateNewTagText("our")
        viewModel.addCustomTag()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.newTagText)
        assertEquals(listOf(1L), state.selectedTagIds)
        assertEquals("our", state.availableTags.single().label)
        assertTrue(state.hasContent)
    }

    @Test
    fun `compose tag preview only exposes selected tags and first ten suggestions`() = runTest(dispatcher) {
        val tagDao = FakeTagDao(initialTags = (1L..100L).map { id -> testTag(id, "태그$id") })
        val viewModel = MediaComposeViewModel(SavedStateHandle(), FakeMediaRepository(), tagDao)
        advanceUntilIdle()

        viewModel.toggleTag(50L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(100, state.availableTags.size)
        assertEquals(listOf(50L), state.selectedTags.map { it.id })
        assertEquals(10, state.suggestedTags.size)
        assertTrue(state.suggestedTags.none { it.id == 50L })
        assertEquals(89, state.hiddenTagCount)
    }

    @Test
    fun `tag search filters many tags and selected tag appears first`() = runTest(dispatcher) {
        val tagDao = FakeTagDao(initialTags = (1L..100L).map { id -> testTag(id, "태그$id") })
        val viewModel = MediaComposeViewModel(SavedStateHandle(), FakeMediaRepository(), tagDao)
        advanceUntilIdle()

        viewModel.toggleTag(92L)
        viewModel.updateTagSearchQuery("태그92")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(92L), state.searchedTags.map { it.id })
        assertEquals(false, state.canCreateSearchTag)
    }

    @Test
    fun `addCustomTagFromSearch creates selects and clears search query`() = runTest(dispatcher) {
        val tagDao = FakeTagDao(initialTags = (1L..100L).map { id -> testTag(id, "태그$id") })
        val viewModel = MediaComposeViewModel(SavedStateHandle(), FakeMediaRepository(), tagDao)
        advanceUntilIdle()

        viewModel.updateTagSearchQuery("새개인태그")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canCreateSearchTag)

        viewModel.addCustomTagFromSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.tagSearchQuery)
        assertTrue(state.availableTags.any { it.label == "새개인태그" })
        assertTrue(state.selectedTags.any { it.label == "새개인태그" })
    }

    @Test
    fun `removeMediaAt removes selected uri and ignores invalid index`() = runTest(dispatcher) {
        val viewModel = MediaComposeViewModel(SavedStateHandle(), FakeMediaRepository(), FakeTagDao())
        val first = Uri.parse("content://memorix/photo/1")
        val second = Uri.parse("content://memorix/photo/2")
        val third = Uri.parse("content://memorix/video/3")
        viewModel.setMediaUris(listOf(first, second, third))

        viewModel.removeMediaAt(1)
        viewModel.removeMediaAt(99)
        advanceUntilIdle()

        assertEquals(listOf(first, third), viewModel.uiState.value.mediaUris)
    }

    @Test
    fun `save passes selected custom tag ids to repository`() = runTest(dispatcher) {
        val tagDao = FakeTagDao()
        val repository = FakeMediaRepository()
        val viewModel = MediaComposeViewModel(SavedStateHandle(), repository, tagDao)
        advanceUntilIdle()

        viewModel.setMediaUris(listOf(Uri.parse("content://memorix/photo/1")))
        viewModel.updateNewTagText("계약")
        viewModel.addCustomTag()
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.lastTagIds)
        assertTrue(viewModel.uiState.value.saveComplete)
    }

    @Test
    fun `setMediaUris exposes first photo or video creation time as event date`() = runTest(dispatcher) {
        val uri = Uri.parse("content://memorix/photo/created-at")
        val repository = FakeMediaRepository(
            previewResult = ImportPreview(
                items = listOf(
                    com.mebonsoft.memorix.core.media.ImportPreviewItem(
                        uri = uri,
                        sourceId = uri.toString(),
                        displayName = "created.jpg",
                        mediaType = com.mebonsoft.memorix.core.database.entity.MediaType.PHOTO,
                        takenAtEpochMillis = 1_725_600_000_000L,
                        fileSizeKb = 512L,
                    )
                )
            )
        )
        val viewModel = MediaComposeViewModel(SavedStateHandle(), repository, FakeTagDao())

        viewModel.setMediaUris(listOf(uri))
        advanceUntilIdle()

        assertEquals(1_725_600_000_000L, viewModel.uiState.value.eventDateMillis)
        assertTrue(viewModel.uiState.value.eventDateHint.contains("생성 시간"))
    }

    @Test
    fun `setMediaUris uses current time when creation time cannot be detected`() = runTest(dispatcher) {
        val uri = Uri.parse("content://memorix/photo/no-created-at")
        val viewModel = MediaComposeViewModel(SavedStateHandle(), FakeMediaRepository(), FakeTagDao())
        val before = System.currentTimeMillis()

        viewModel.setMediaUris(listOf(uri))
        advanceUntilIdle()

        val eventDateMillis = viewModel.uiState.value.eventDateMillis ?: error("eventDateMillis should be set")
        val after = System.currentTimeMillis()
        assertTrue(eventDateMillis in before..after)
        assertTrue(viewModel.uiState.value.eventDateHint.contains("현재 시간"))
    }

    @Test
    fun `save imports all shared media uris in order into selected space`() = runTest(dispatcher) {
        val repository = FakeMediaRepository()
        val viewModel = MediaComposeViewModel(SavedStateHandle(), repository, FakeTagDao())
        val first = Uri.parse("content://share/photo/1")
        val second = Uri.parse("content://share/video/2")
        val third = Uri.parse("content://share/photo/3")

        viewModel.setMediaUris(listOf(first, second, third))
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf(first, second, third), repository.lastImportedUris)
        assertEquals(MediaSpace.WORK, repository.lastImportSpace)
        assertTrue(viewModel.uiState.value.saveComplete)
    }
}

private class FakeTagDao(
    initialTags: List<TagEntity> = emptyList(),
) : TagDao {
    private val tags = MutableStateFlow(initialTags)
    private var nextId = (initialTags.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeTags(): Flow<List<TagEntity>> = tags

    override fun observeManagedTags(): Flow<List<com.mebonsoft.memorix.core.database.dao.ManagedTagSummary>> = flowOf(emptyList())

    override fun observeTagsForMedia(mediaId: Long): Flow<List<TagEntity>> = flowOf(emptyList())

    override fun observeMediaTagAssignments(): Flow<List<MediaTagAssignment>> = flowOf(emptyList())

    override fun observeTopTags(limit: Int): Flow<List<TagUsageSummary>> = flowOf(emptyList())
    override fun observeTopTagsForSpace(space: MediaSpace, limit: Int): Flow<List<TagUsageSummary>> = flowOf(emptyList())

    override suspend fun insert(tag: TagEntity): Long {
        val id = tag.id.takeIf { it != 0L } ?: nextId++
        tags.value = tags.value + tag.copy(id = id)
        return id
    }

    override suspend fun replaceMediaTags(crossRefs: List<MediaTagCrossRef>) = Unit

    override suspend fun clearMediaTags(mediaId: Long) = Unit
    override suspend fun clearTagAssignments(tagId: Long) = Unit
    override suspend fun deleteTagById(tagId: Long) = Unit
    override suspend fun deleteManagedTag(tagId: Long) = Unit
}

private fun testTag(
    id: Long,
    label: String,
) = TagEntity(
    id = id,
    key = label.lowercase(),
    label = label,
    colorHex = "#005A46",
    iconName = "tag",
    isCustom = true,
)

private class FakeMediaRepository(
    private val previewResult: ImportPreview = ImportPreview(items = emptyList()),
) : MediaRepository {
    var lastTagIds: List<Long> = emptyList()
    var lastImportedUris: List<Uri> = emptyList()
    var lastImportSpace: MediaSpace? = null

    override fun observeLibrary(): Flow<List<MediaItemEntity>> = flowOf(emptyList())

    override fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>> = flowOf(emptyList())

    override fun observeAlbum(albumId: Long): Flow<List<MediaItemEntity>> = flowOf(emptyList())

    override fun observeMedia(mediaId: Long): Flow<MediaItemEntity?> = flowOf(null)

    override suspend fun previewImport(uris: List<Uri>, dateRange: ImportDateRange?): ImportPreview = previewResult

    override suspend fun importMedia(uris: List<Uri>, space: MediaSpace): List<Long> = listOf(1L)

    override suspend fun importMediaWithMetadata(
        uris: List<Uri>,
        space: MediaSpace,
        note: String,
        tagIds: List<Long>,
        countryCode: String,
        region: String,
        batchGroupId: String?,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): List<Long> {
        lastTagIds = tagIds
        lastImportedUris = uris
        lastImportSpace = space
        uris.forEachIndexed { index, _ -> onProgress(index + 1, uris.size) }
        return listOf(1L)
    }

    override suspend fun updateMedia(item: MediaItemEntity) = Unit

    override suspend fun rebuildSearchIndex(item: MediaItemEntity) = Unit
}
