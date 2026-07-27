package com.mebonsoft.memorix.feature.work.compose

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.database.entity.TagEntity
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val tagDao: TagDao,
) : ViewModel() {

    private companion object {
        const val MediaUrisKey = "media_uris"
        const val SpaceKey = "media_space"
    }

    private val formState = MutableStateFlow(
        ComposeFormState(
            mediaUris = savedStateHandle.get<List<String>>(MediaUrisKey)
                .orEmpty()
                .map(Uri::parse),
            space = savedStateHandle.get<String>(SpaceKey)
                ?.let { runCatching { MediaSpace.valueOf(it) }.getOrNull() }
                ?: MediaSpace.WORK,
        )
    )

    val allTags: StateFlow<List<TagEntity>> = tagDao.observeTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<ComposeUiState> = combine(formState, allTags) { form, tags ->
        ComposeUiState(
            mediaUris = form.mediaUris,
            eventDateMillis = form.eventDateMillis,
            eventDateHint = form.eventDateHint,
            space = form.space,
            note = form.note,
            selectedTagIds = form.selectedTagIds,
            newTagText = form.newTagText,
            countryCode = form.countryCode,
            region = form.region,
            locationHint = form.locationHint,
            availableTags = tags,
            isSaving = form.isSaving,
            saveComplete = form.saveComplete,
            errorMessage = form.errorMessage,
            hasContent = form.mediaUris.isNotEmpty() || form.note.isNotBlank() || form.selectedTagIds.isNotEmpty() || form.newTagText.isNotBlank()
                || form.countryCode.isNotBlank() || form.region.isNotBlank(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ComposeUiState())

    fun setMediaUris(uris: List<Uri>) {
        savedStateHandle[MediaUrisKey] = uris.map { it.toString() }
        formState.update { it.copy(mediaUris = uris) }
        refreshEventDateFromMedia(uris)
    }

    fun setSpace(space: MediaSpace) {
        savedStateHandle[SpaceKey] = space.name
        formState.update { it.copy(space = space) }
    }

    fun addMediaUris(uris: List<Uri>) {
        formState.update {
            val updated = it.mediaUris + uris
            savedStateHandle[MediaUrisKey] = updated.map { uri -> uri.toString() }
            it.copy(mediaUris = updated)
        }
        refreshEventDateFromMedia(formState.value.mediaUris)
    }

    fun removeMediaAt(index: Int) {
        formState.update { state ->
            if (index !in state.mediaUris.indices) return@update state
            val updated = state.mediaUris.toMutableList().apply { removeAt(index) }
            savedStateHandle[MediaUrisKey] = updated.map { it.toString() }
            state.copy(mediaUris = updated)
        }
        refreshEventDateFromMedia(formState.value.mediaUris)
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

    fun updateNewTagText(text: String) {
        formState.update { it.copy(newTagText = text) }
    }

    fun addCustomTag() {
        val label = formState.value.newTagText.trim().trimStart('#')
        if (label.isBlank()) return
        viewModelScope.launch {
            val key = normalizeTagKey(label)
            val existing = allTags.value.firstOrNull { it.key == key || it.label.equals(label, ignoreCase = true) }
            val tagId = existing?.id ?: tagDao.insert(
                TagEntity(
                    key = key,
                    label = label,
                    colorHex = defaultTagColor(label),
                    iconName = "tag",
                    isCustom = true,
                )
            )
            formState.update { state ->
                state.copy(
                    selectedTagIds = (state.selectedTagIds + tagId).distinct(),
                    newTagText = "",
                )
            }
        }
    }

    fun updateCountryCode(code: String) {
        formState.update { it.copy(countryCode = code) }
    }

    fun updateRegion(region: String) {
        formState.update { it.copy(region = region) }
    }

    fun applyAutoLocation(countryCode: String, region: String, message: String? = null) {
        formState.update {
            it.copy(
                countryCode = countryCode,
                region = region,
                locationHint = message ?: "첫 번째 사진의 위치정보를 자동으로 입력했습니다.",
            )
        }
    }

    fun showLocationHint(message: String) {
        formState.update { it.copy(locationHint = message) }
    }

    private fun refreshEventDateFromMedia(uris: List<Uri>) {
        viewModelScope.launch {
            if (uris.isEmpty()) {
                formState.update {
                    it.copy(
                        eventDateMillis = null,
                        eventDateHint = "사진·영상 생성 시간이 있으면 이벤트 날짜로 자동 표시됩니다.",
                    )
                }
                return@launch
            }
            runCatching { mediaRepository.previewImport(uris) }
                .onSuccess { preview ->
                    val eventItem = preview.items.firstOrNull { item ->
                        item.takenAtEpochMillis != null && (item.mediaType == MediaType.PHOTO || item.mediaType == MediaType.VIDEO)
                    }
                    val eventMillis = eventItem?.takenAtEpochMillis ?: System.currentTimeMillis()
                    formState.update {
                        it.copy(
                            eventDateMillis = eventMillis,
                            eventDateHint = eventItem?.let { "사진·영상 생성 시간 기준 · ${formatEventDate(eventMillis)}" }
                                ?: "생성 시간을 찾지 못해 현재 시간으로 표시합니다. 저장 후 상세에서 수정할 수 있습니다.",
                        )
                    }
                }
                .onFailure {
                    val eventMillis = System.currentTimeMillis()
                    formState.update {
                        it.copy(
                            eventDateMillis = eventMillis,
                            eventDateHint = "이벤트 날짜를 읽지 못해 현재 시간으로 표시합니다. 저장 후 상세에서 수정할 수 있습니다.",
                        )
                    }
                }
        }
    }

    fun save() {
        val current = formState.value
        if (current.mediaUris.isEmpty() || current.isSaving) return
        viewModelScope.launch {
            formState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                mediaRepository.importMediaWithMetadata(
                    uris = current.mediaUris,
                    space = current.space,
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
    val eventDateMillis: Long? = null,
    val eventDateHint: String = "사진·영상 생성 시간이 있으면 이벤트 날짜로 자동 표시됩니다.",
    val space: MediaSpace = MediaSpace.WORK,
    val note: String = "",
    val selectedTagIds: List<Long> = emptyList(),
    val newTagText: String = "",
    val countryCode: String = "",
    val region: String = "",
    val locationHint: String = "",
    val availableTags: List<TagEntity> = emptyList(),
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
    val errorMessage: String? = null,
    val hasContent: Boolean = false,
)

private data class ComposeFormState(
    val mediaUris: List<Uri> = emptyList(),
    val eventDateMillis: Long? = null,
    val eventDateHint: String = "사진·영상 생성 시간이 있으면 이벤트 날짜로 자동 표시됩니다.",
    val space: MediaSpace = MediaSpace.WORK,
    val note: String = "",
    val selectedTagIds: List<Long> = emptyList(),
    val newTagText: String = "",
    val countryCode: String = "",
    val region: String = "",
    val locationHint: String = "사진에 위치정보가 있으면 첫 번째 사진 기준으로 자동 표시됩니다. 위치정보가 없으면 직접 입력해 주세요.",
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
    val errorMessage: String? = null,
)

private val eventDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

private fun formatEventDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(eventDateFormatter)

private fun normalizeTagKey(label: String): String = label
    .trim()
    .trimStart('#')
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), "-")
    .replace(Regex("[^0-9a-z가-힣_-]"), "")
    .ifBlank { "tag-${System.currentTimeMillis()}" }

private fun defaultTagColor(label: String): String {
    val palette = listOf("#005A46", "#1A73E8", "#C23A70", "#5142D7", "#B25E00")
    return palette[kotlin.math.abs(label.hashCode()) % palette.size]
}
