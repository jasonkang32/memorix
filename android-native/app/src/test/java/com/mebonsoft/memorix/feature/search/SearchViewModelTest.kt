package com.mebonsoft.memorix.feature.search

import com.mebonsoft.memorix.core.database.entity.*
import com.mebonsoft.memorix.data.repository.SearchRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun dateRangeFiltersResultsWithoutQueryAndClearsWithoutChangingOtherFilters() = runTest(dispatcher) {
        val day = LocalDate.of(2026, 11, 8)
        fun photo(id: Long, date: LocalDate) = MediaItemEntity(
            id = id, mediaType = MediaType.PHOTO, filePath = "photo-$id.jpg",
            takenAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), createdAt = 0,
        )
        val candidates = listOf(photo(1, day.minusDays(1)), photo(2, day), photo(3, day.plusDays(1)))
        val repository = object : SearchRepository {
            override fun observeAlbumFilters(): Flow<List<AlbumSummary>> = flowOf(emptyList())
            override fun observeResults(rawQuery: String, space: MediaSpace?, albumId: Long?, mediaType: MediaType?) = flowOf(candidates)
        }
        val vm = SearchViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.updateMediaType(MediaType.PHOTO)
        vm.updateDateRange(day, day)
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.uiState.value.results.map { it.id })
        assertEquals(SearchDateRange(day, day), vm.uiState.value.dateRange)
        assertTrue(vm.uiState.value.summary.contains("2026-11-08"))

        vm.updateDateRange(day, day.minusDays(1))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.results.isEmpty())
        assertFalse(vm.uiState.value.dateRange.isValid)

        vm.updateDateRange(null, null)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), vm.uiState.value.results.map { it.id })
        assertEquals(MediaType.PHOTO, vm.uiState.value.selectedMediaType)
    }
}
