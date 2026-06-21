package com.jasonkang.memorix.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.database.entity.MediaType
import com.jasonkang.memorix.data.repository.MediaRepository
import com.jasonkang.memorix.feature.search.SearchSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class WorkViewModel @Inject constructor(
    mediaRepository: MediaRepository,
) : ViewModel() {
    private val controls = MutableStateFlow(WorkControls())

    val uiState: StateFlow<WorkUiState> = combine(
        mediaRepository.observeSpace(MediaSpace.WORK),
        controls,
    ) { items, controls ->
        val filteredItems = items.filter { item ->
            SearchSupport.matchesLocal(item, controls.query) &&
                (controls.mediaType == null || item.mediaType == controls.mediaType)
        }
        WorkUiState(
            items = items,
            filteredItems = filteredItems,
            query = controls.query,
            selectedMediaType = controls.mediaType,
            summary = buildSummary(filteredItems, controls.mediaType),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkUiState(),
    )

    fun updateQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun updateMediaType(mediaType: MediaType?) {
        controls.update { it.copy(mediaType = mediaType) }
    }
}

data class WorkUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val filteredItems: List<MediaItemEntity> = emptyList(),
    val query: String = "",
    val selectedMediaType: MediaType? = null,
    val summary: String = "전체 0개",
)

private data class WorkControls(
    val query: String = "",
    val mediaType: MediaType? = null,
)

private fun buildSummary(items: List<MediaItemEntity>, selectedMediaType: MediaType?): String {
    val count = items.size
    val prefix = SearchSupport.mediaTypeLabel(selectedMediaType)
    return if (selectedMediaType == null) {
        "전체 ${count}개"
    } else {
        "$prefix ${count}개"
    }
}
