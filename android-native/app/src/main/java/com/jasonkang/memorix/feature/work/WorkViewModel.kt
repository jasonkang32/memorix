package com.jasonkang.memorix.feature.work

import android.net.Uri
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
import kotlinx.coroutines.launch

@HiltViewModel
class WorkViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
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
            isImporting = controls.isImporting,
            importMessage = controls.importMessage,
            errorMessage = controls.errorMessage,
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

    fun importMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            controls.update { it.copy(isImporting = true, importMessage = null, errorMessage = null) }
            runCatching { mediaRepository.importMedia(uris, MediaSpace.WORK) }
                .onSuccess { importedIds ->
                    controls.update {
                        it.copy(
                            isImporting = false,
                            importMessage = "Work에 ${importedIds.size}개 항목을 등록했습니다.",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    controls.update {
                        it.copy(
                            isImporting = false,
                            importMessage = null,
                            errorMessage = error.message ?: "Work 등록에 실패했습니다.",
                        )
                    }
                }
        }
    }

    fun consumeImportMessages() {
        controls.update { it.copy(importMessage = null, errorMessage = null) }
    }
}

data class WorkUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val filteredItems: List<MediaItemEntity> = emptyList(),
    val query: String = "",
    val selectedMediaType: MediaType? = null,
    val summary: String = "전체 0개",
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
)

private data class WorkControls(
    val query: String = "",
    val mediaType: MediaType? = null,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val errorMessage: String? = null,
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
