package com.mebonsoft.memorix.feature.detail

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.TagEntity
import com.mebonsoft.memorix.data.repository.AlbumRepository
import com.mebonsoft.memorix.data.repository.MediaRepository
import com.mebonsoft.memorix.core.monetization.ProEntitlement
import com.mebonsoft.memorix.core.monetization.ProEntitlementRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
    private val tagDao: TagDao,
    private val entitlementRepository: ProEntitlementRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"])
    private val transientState = MutableStateFlow(MediaDetailTransientState())

    private val itemWithRelatedItems = combine(
        mediaRepository.observeMedia(mediaId),
        mediaRepository.observeLibrary(),
    ) { item, libraryItems ->
        item to item?.let { MediaEditorSupport.relatedWorkItems(it, libraryItems) }.orEmpty()
    }

    private val entitlementWithTransient = combine(
        entitlementRepository.entitlement,
        transientState,
    ) { entitlement, transient -> entitlement to transient }

    val uiState: StateFlow<MediaDetailUiState> = combine(
        itemWithRelatedItems,
        albumRepository.observeAlbumSummaries(),
        tagDao.observeTags(),
        tagDao.observeTagsForMedia(mediaId),
        entitlementWithTransient,
    ) { itemBundle, albums, tags, selectedTags, entitlementBundle ->
        val (item, relatedItems) = itemBundle
        val (entitlement, transient) = entitlementBundle
        MediaDetailUiState(
            item = item,
            relatedItems = relatedItems,
            albums = albums,
            availableTags = tags,
            selectedTagIds = selectedTags.map { it.id },
            entitlement = entitlement,
            isSaving = transient.isSaving,
            isLocating = transient.isLocating,
            isOcrRunning = transient.isOcrRunning,
            message = transient.message,
            deleted = transient.deleted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaDetailUiState(),
    )

    fun saveDraft(
        item: MediaItemEntity,
        title: String,
        note: String,
        countryCode: String,
        region: String,
        takenAt: Long,
        selectedTagIds: List<Long>,
        isSecret: Boolean = item.isSecret,
        relatedItems: List<MediaItemEntity> = emptyList(),
    ) {
        viewModelScope.launch {
            transientState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val groupItems = (relatedItems.ifEmpty { listOf(item) }).distinctBy { it.id }
                val resolvedBatchGroupId = item.batchGroupId.ifBlank {
                    groupItems.firstOrNull { it.batchGroupId.isNotBlank() }?.batchGroupId.orEmpty()
                }
                val updated = item.copy(
                    title = title.trim(),
                    note = note.trim(),
                    countryCode = countryCode.trim(),
                    region = region.trim(),
                    batchGroupId = resolvedBatchGroupId,
                    takenAt = takenAt,
                    isSecret = isSecret,
                )
                groupItems.forEach { groupItem ->
                    mediaRepository.updateMedia(
                        if (groupItem.id == item.id) updated else groupItem.copy(
                            note = note.trim(),
                            countryCode = countryCode.trim(),
                            region = region.trim(),
                            batchGroupId = groupItem.batchGroupId.ifBlank { resolvedBatchGroupId },
                            isSecret = isSecret,
                        ),
                    )
                }
                tagDao.setMediaTags(item.id, selectedTagIds)
            }.onSuccess {
                transientState.update { it.copy(isSaving = false, message = "저장했습니다.") }
            }.onFailure { error ->
                transientState.update { it.copy(isSaving = false, message = error.message ?: "저장에 실패했습니다.") }
            }
        }
    }

    fun addCustomTag(label: String, currentSelectedTagIds: List<Long>) {
        val normalized = label.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            val existing = uiState.value.availableTags.firstOrNull { it.label == normalized }
            val tagId = existing?.id ?: tagDao.insert(
                TagEntity(
                    key = "custom_${normalized.lowercase(Locale.getDefault()).replace(Regex("\\s+"), "_")}",
                    label = normalized,
                    colorHex = "#00C896",
                    iconName = "label",
                    isCustom = true,
                )
            )
            tagDao.setMediaTags(mediaId, (currentSelectedTagIds + tagId).distinct())
        }
    }

    fun addMediaToItem(
        uris: List<Uri>,
        item: MediaItemEntity,
        countryCode: String,
        region: String,
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            transientState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val groupId = item.batchGroupId.ifBlank { UUID.randomUUID().toString() }
                if (item.batchGroupId.isBlank()) {
                    mediaRepository.updateMedia(item.copy(batchGroupId = groupId))
                }
                mediaRepository.importMediaWithMetadata(
                    uris = uris,
                    space = item.space,
                    note = "",
                    tagIds = emptyList(),
                    countryCode = countryCode.trim(),
                    region = region.trim(),
                    batchGroupId = groupId,
                )
            }.onSuccess { ids ->
                transientState.update { it.copy(isSaving = false, message = "미디어 ${ids.size}개를 추가했습니다.") }
            }.onFailure { error ->
                transientState.update { it.copy(isSaving = false, message = error.message ?: "미디어 추가에 실패했습니다.") }
            }
        }
    }

    fun autoFillLocation(filePath: String, onResult: (LocationDraft?) -> Unit) {
        viewModelScope.launch {
            transientState.update { it.copy(isLocating = true, message = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val latLong = ExifInterface(filePath).latLong ?: return@withContext null
                    val lat = latLong[0].toDouble()
                    val lng = latLong[1].toDouble()
                    val placemark = Geocoder(appContext, Locale.KOREAN)
                        .getFromLocation(lat, lng, 1)
                        ?.firstOrNull()
                    LocationDraft(
                        countryCode = placemark?.countryName ?: placemark?.countryCode ?: "",
                        region = placemark?.adminArea ?: placemark?.locality ?: "",
                    )
                }
            }.getOrNull()
            onResult(result)
            transientState.update {
                it.copy(
                    isLocating = false,
                    message = if (result == null) null else "GPS 위치를 입력했습니다.",
                )
            }
        }
    }

    fun runOcr(item: MediaItemEntity) {
        if (item.mediaType == com.mebonsoft.memorix.core.database.entity.MediaType.VIDEO) return
        viewModelScope.launch {
            transientState.update { it.copy(isOcrRunning = true, message = null) }
            runCatching {
                val text = recognizeText(item.filePath).trim()
                mediaRepository.updateMedia(item.copy(ocrText = text))
                text
            }.onSuccess { text ->
                transientState.update {
                    it.copy(
                        isOcrRunning = false,
                        message = if (text.isBlank()) "인식된 텍스트가 없습니다." else "텍스트 인식이 완료되었습니다.",
                    )
                }
            }.onFailure { error ->
                transientState.update {
                    it.copy(
                        isOcrRunning = false,
                        message = error.message ?: "텍스트 인식에 실패했습니다.",
                    )
                }
            }
        }
    }

    private suspend fun recognizeText(filePath: String): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val image = runCatching { InputImage.fromFilePath(appContext, Uri.fromFile(File(filePath))) }
            .getOrElse { error ->
                recognizer.close()
                continuation.resumeWithException(error)
                return@suspendCancellableCoroutine
            }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                recognizer.close()
                if (continuation.isActive) continuation.resume(result.text)
            }
            .addOnFailureListener { error ->
                recognizer.close()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        continuation.invokeOnCancellation { recognizer.close() }
    }

    fun deleteMedia(item: MediaItemEntity, relatedItems: List<MediaItemEntity> = emptyList()) {
        viewModelScope.launch {
            transientState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val targets = MediaEditorSupport.workDeleteTargets(item, relatedItems)
                trashMediaItems(targets)
                targets.size
            }.onSuccess { deletedCount ->
                val message = if (deletedCount > 1) "${detailSpaceLabel(item.space)} 미디어를 삭제했습니다." else "미디어를 삭제했습니다."
                transientState.update { it.copy(isSaving = false, deleted = true, message = message) }
            }.onFailure { error ->
                transientState.update { it.copy(isSaving = false, message = error.message ?: "삭제에 실패했습니다.") }
            }
        }
    }

    fun removeMediaFromGroup(item: MediaItemEntity, closeDetail: Boolean) {
        viewModelScope.launch {
            transientState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                trashMediaItems(listOf(item))
            }.onSuccess {
                transientState.update {
                    it.copy(
                        isSaving = false,
                        deleted = closeDetail,
                        message = "미디어를 제거했습니다.",
                    )
                }
            }.onFailure { error ->
                transientState.update { it.copy(isSaving = false, message = error.message ?: "제거에 실패했습니다.") }
            }
        }
    }

    private suspend fun trashMediaItems(items: List<MediaItemEntity>) {
        items.distinctBy { it.id }.forEach { target ->
            withContext(Dispatchers.IO) {
                File(target.filePath).delete()
                target.thumbPath?.let { File(it).delete() }
            }
            mediaRepository.updateMedia(target.copy(isTrashed = true))
            tagDao.clearMediaTags(target.id)
        }
    }

    fun consumeMessage() {
        transientState.update { it.copy(message = null) }
    }
}

data class MediaDetailUiState(
    val item: MediaItemEntity? = null,
    val relatedItems: List<MediaItemEntity> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val availableTags: List<TagEntity> = emptyList(),
    val selectedTagIds: List<Long> = emptyList(),
    val entitlement: ProEntitlement = ProEntitlement.Free,
    val isSaving: Boolean = false,
    val isLocating: Boolean = false,
    val isOcrRunning: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false,
)

data class LocationDraft(
    val countryCode: String,
    val region: String,
)

private fun detailSpaceLabel(space: com.mebonsoft.memorix.core.database.entity.MediaSpace): String = when (space) {
    com.mebonsoft.memorix.core.database.entity.MediaSpace.WORK -> "업무"
    com.mebonsoft.memorix.core.database.entity.MediaSpace.PERSONAL -> "개인"
}

private data class MediaDetailTransientState(
    val isSaving: Boolean = false,
    val isLocating: Boolean = false,
    val isOcrRunning: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false,
)
