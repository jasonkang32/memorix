package com.jasonkang.memorix.feature.work.compose

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.database.entity.TagEntity
import com.jasonkang.memorix.core.database.dao.TagDao
import com.jasonkang.memorix.data.repository.MediaRepository
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
class MediaComposeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val tagDao: TagDao,
) : ViewModel() {

    private val formState = MutableStateFlow(ComposeFormState())

    val allTags: StateFlow<List<TagEntity>> = tagDao.observeTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<ComposeUiState> = combine(formState, allTags) { form, tags ->
        ComposeUiState(
            mediaUris = form.mediaUris,
            note = form.note,
            selectedTagIds = form.selectedTagIds,
            countryCode = form.countryCode,
            region = form.region,
            availableTags = tags,
            isSaving = form.isSaving,
            saveComplete = form.saveComplete,
            errorMessage = form.errorMessage,
            hasContent = form.note.isNotBlank() || form.selectedTagIds.isNotEmpty()
                || form.countryCode.isNotBlank() || form.region.isNotBlank(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ComposeUiState())

    fun setMediaUris(uris: List<Uri>) {
        formState.update { it.copy(mediaUris = uris) }
    }

    fun addMediaUris(uris: List<Uri>) {
        formState.update { it.copy(mediaUris = it.mediaUris + uris) }
    }

    fun removeMediaAt(index: Int) {
        formState.update { state ->
            val updated = state.mediaUris.toMutableList().apply { removeAt(index) }
            state.copy(mediaUris = updated)
        }
    }

    fun updateNote(note: String) {
        formState.update { it.copy(note = note) }
    }

    fun toggleTag(tagId: Long) {
        formState.update { state ->
            val updated = if (tagId in state.selectedTagIds) {
                state.selectedTagIds - tagId
            } else {
                state.selectedTagIds + tagId
            }
            state.copy(selectedTagIds = updated)
        }
    }

    fun updateCountryCode(code: String) {
        formState.update { it.copy(countryCode = code) }
    }

    fun updateRegion(region: String) {
        formState.update { it.copy(region = region) }
    }

    fun save() {
        val current = formState.value
        if (current.mediaUris.isEmpty() || current.isSaving) return
        viewModelScope.launch {
            formState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                mediaRepository.importMediaWithMetadata(
                    uris = current.mediaUris,
                    space = MediaSpace.WORK,
                    note = current.note,
                    tagIds = current.selectedTagIds,
                    countryCode = current.countryCode,
                    region = current.region,
                )
            }.onSuccess {
                formState.update { it.copy(isSaving = false, saveComplete = true) }
            }.onFailure { error ->
                formState.update {
                    it.copy(isSaving = false, errorMessage = error.message ?: "저장에 실패했습니다.")
                }
            }
        }
    }
}

data class ComposeUiState(
    val mediaUris: List<Uri> = emptyList(),
    val note: String = "",
    val selectedTagIds: List<Long> = emptyList(),
    val countryCode: String = "",
    val region: String = "",
    val availableTags: List<TagEntity> = emptyList(),
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
    val errorMessage: String? = null,
    val hasContent: Boolean = false,
)

private data class ComposeFormState(
    val mediaUris: List<Uri> = emptyList(),
    val note: String = "",
    val selectedTagIds: List<Long> = emptyList(),
    val countryCode: String = "",
    val region: String = "",
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
    val errorMessage: String? = null,
)
