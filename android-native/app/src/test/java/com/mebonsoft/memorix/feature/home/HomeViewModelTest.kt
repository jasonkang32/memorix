package com.mebonsoft.memorix.feature.home

import android.net.Uri
import com.mebonsoft.memorix.core.backup.ManagedStorageUsage
import com.mebonsoft.memorix.core.backup.MemorixBackupOperations
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.media.ImportDateRange
import com.mebonsoft.memorix.core.media.ImportPreview
import com.mebonsoft.memorix.core.media.ImportPreviewItem
import com.mebonsoft.memorix.data.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private lateinit var dispatcher: kotlinx.coroutines.test.TestDispatcher

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun importMedia_whenImportSucceeds_setsSuccessMessageWithImportedCount() = runTest(dispatcher) {
        val repository = FakeMediaRepository(importResult = listOf(1L, 2L, 3L))
        val viewModel = HomeViewModel(repository, FakeBackupOperations())

        viewModel.importMedia(listOf(Uri.EMPTY, Uri.EMPTY, Uri.EMPTY))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isImporting)
        assertNull(state.errorMessage)
        assertEquals("Work에 3개 항목을 가져왔습니다.", state.importSummaryMessage)
    }

    @Test
    fun previewImport_whenPreviewSucceeds_exposesPendingPreview() = runTest(dispatcher) {
        val preview = ImportPreview(
            items = listOf(
                ImportPreviewItem(
                    uri = Uri.parse("content://preview/1"),
                    sourceId = "content://preview/1",
                    displayName = "sample.jpg",
                    mediaType = MediaType.PHOTO,
                    takenAtEpochMillis = 1_715_000_000_000,
                    fileSizeKb = 320,
                )
            )
        )
        val repository = FakeMediaRepository(previewResult = preview)
        val viewModel = HomeViewModel(repository, FakeBackupOperations())

        viewModel.previewImport(listOf(Uri.parse("content://preview/1")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isImporting)
        assertNull(state.errorMessage)
        assertEquals(1, state.pendingImportPreview?.items?.size)
        assertNull(state.importSummaryMessage)
    }

    @Test
    fun confirmImport_whenPreviewExists_importsPreviewUrisToSelectedSpaceAndClearsDialog() = runTest(dispatcher) {
        val previewUri = Uri.parse("content://preview/confirm")
        val preview = ImportPreview(
            items = listOf(
                ImportPreviewItem(
                    uri = previewUri,
                    sourceId = previewUri.toString(),
                    displayName = "confirm.jpg",
                    mediaType = MediaType.PHOTO,
                    takenAtEpochMillis = null,
                    fileSizeKb = 100,
                )
            )
        )
        val repository = FakeMediaRepository(previewResult = preview, importResult = listOf(10L))
        val viewModel = HomeViewModel(repository, FakeBackupOperations())

        viewModel.previewImport(listOf(previewUri))
        advanceUntilIdle()
        viewModel.selectImportSpace(MediaSpace.PERSONAL)
        advanceUntilIdle()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(listOf(previewUri), repository.lastImportedUris)
        assertEquals(MediaSpace.PERSONAL, repository.lastImportedSpace)
        assertNull(viewModel.uiState.value.pendingImportPreview)
        assertEquals("Personal에 1개 항목을 가져왔습니다.", viewModel.uiState.value.importSummaryMessage)
    }

    @Test
    fun importMedia_withoutSelectingSpace_defaultsToWorkSpace() = runTest(dispatcher) {
        val repository = FakeMediaRepository(importResult = listOf(1L))
        val viewModel = HomeViewModel(repository, FakeBackupOperations())

        viewModel.importMedia(listOf(Uri.EMPTY))
        advanceUntilIdle()

        assertEquals(MediaSpace.WORK, repository.lastImportedSpace)
        assertEquals("Work에 1개 항목을 가져왔습니다.", viewModel.uiState.value.importSummaryMessage)
    }

    @Test
    fun importMedia_whenImportFails_setsErrorAndClearsSuccessMessage() = runTest(dispatcher) {
        val repository = FakeMediaRepository(error = IllegalStateException("가져오기 실패"))
        val viewModel = HomeViewModel(repository, FakeBackupOperations())

        viewModel.importMedia(listOf(Uri.EMPTY))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isImporting)
        assertEquals("가져오기 실패", state.errorMessage)
        assertNull(state.importSummaryMessage)
    }

    @Test
    fun cancelImport_whenImportIsRunning_stopsProgressAndClearsTransientImportState() = runTest(dispatcher) {
        val previewUri = Uri.parse("content://preview/cancel")
        val preview = ImportPreview(
            items = listOf(
                ImportPreviewItem(
                    uri = previewUri,
                    sourceId = previewUri.toString(),
                    displayName = "cancel.jpg",
                    mediaType = MediaType.PHOTO,
                    takenAtEpochMillis = null,
                    fileSizeKb = 100,
                )
            )
        )
        val repository = FakeMediaRepository(
            previewResult = preview,
            importResult = listOf(1L),
            importDelayMillis = Long.MAX_VALUE,
        )
        val viewModel = HomeViewModel(repository, FakeBackupOperations())

        viewModel.previewImport(listOf(previewUri))
        advanceUntilIdle()
        viewModel.importMedia(listOf(previewUri), MediaSpace.WORK)
        runCurrent()
        assertTrue(viewModel.uiState.value.isImporting)

        viewModel.cancelImport()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isImporting)
        assertNull(state.pendingImportPreview)
        assertNull(state.importSummaryMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun clearImportSummary_clearsOnlySuccessMessage() = runTest(dispatcher) {
        val repository = FakeMediaRepository(importResult = listOf(1L, 2L, 3L))
        val viewModel = HomeViewModel(repository, FakeBackupOperations())

        viewModel.importMedia(listOf(Uri.EMPTY, Uri.EMPTY, Uri.EMPTY))
        advanceUntilIdle()
        assertEquals("Work에 3개 항목을 가져왔습니다.", viewModel.uiState.value.importSummaryMessage)

        viewModel.clearImportSummary()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.importSummaryMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun uiState_exposesTopTenTagsFromRepository() = runTest(dispatcher) {
        val topTags = (1..12).map { index ->
            TagUsageSummary(
                id = index.toLong(),
                key = "tag-$index",
                label = "태그$index",
                colorHex = "#0F7B57",
                iconName = "tag",
                usageCount = 20 - index,
            )
        }
        val repository = FakeMediaRepository(topTags = topTags.take(10))
        val viewModel = HomeViewModel(repository, FakeBackupOperations())
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.topTags.size)
        assertEquals("태그1", viewModel.uiState.value.topTags.first().label)
        assertEquals(19, viewModel.uiState.value.topTags.first().usageCount)
    }
}

private class FakeMediaRepository(
    private val previewResult: ImportPreview = ImportPreview(items = emptyList()),
    private val importResult: List<Long> = emptyList(),
    private val error: Throwable? = null,
    private val topTags: List<TagUsageSummary> = emptyList(),
    private val importDelayMillis: Long = 0L,
) : MediaRepository {
    var lastImportedUris: List<Uri> = emptyList()
        private set
    var lastImportedSpace: MediaSpace? = null
        private set

    override fun observeLibrary(): Flow<List<MediaItemEntity>> = flowOf(emptyList())

    override fun observeSpace(space: MediaSpace): Flow<List<MediaItemEntity>> = flowOf(emptyList())

    override fun observeAlbum(albumId: Long): Flow<List<MediaItemEntity>> = flowOf(emptyList())

    override fun observeMedia(mediaId: Long): Flow<MediaItemEntity?> = flowOf(null)

    override fun observeTopTags(limit: Int): Flow<List<TagUsageSummary>> = flowOf(topTags.take(limit))

    override suspend fun previewImport(uris: List<Uri>, dateRange: ImportDateRange?): ImportPreview = previewResult

    override suspend fun importMedia(uris: List<Uri>, space: MediaSpace): List<Long> {
        lastImportedUris = uris
        lastImportedSpace = space
        if (importDelayMillis > 0L) delay(importDelayMillis)
        error?.let { throw it }
        return importResult
    }

    override suspend fun updateMedia(item: MediaItemEntity) = Unit

    override suspend fun rebuildSearchIndex(item: MediaItemEntity) = Unit
}

private class FakeBackupOperations(
    private val usage: ManagedStorageUsage = ManagedStorageUsage(mediaBytes = 2_048L, databaseBytes = 1_024L),
) : MemorixBackupOperations {
    override suspend fun calculateManagedStorageUsage(): ManagedStorageUsage = usage
    override suspend fun exportBackup(destination: Uri): ManagedStorageUsage = usage
    override suspend fun restoreBackup(source: Uri): ManagedStorageUsage = usage
    override suspend fun resetAllData(): ManagedStorageUsage = ManagedStorageUsage(mediaBytes = 0L, databaseBytes = 0L)
}
