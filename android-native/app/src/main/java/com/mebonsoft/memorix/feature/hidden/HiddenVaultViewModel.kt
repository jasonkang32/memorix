package com.mebonsoft.memorix.feature.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.auth.AuthRepository
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.data.repository.MediaRepository
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
class HiddenVaultViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository,
    tagDao: TagDao,
) : ViewModel() {
    private val controls = MutableStateFlow(HiddenVaultControls())
    private val tagAssignments = tagDao.observeMediaTagAssignments()

    init {
        viewModelScope.launch {
            authRepository.settings.collect { settings ->
                controls.update {
                    it.copy(
                        hasAuthCredential = settings.hasPin || settings.biometricEnabled,
                        canUseBiometric = settings.biometricEnabled && authRepository.canUseBiometric(),
                    )
                }
            }
        }
    }

    val uiState: StateFlow<HiddenVaultUiState> = combine(
        mediaRepository.observeLibrary(),
        tagAssignments,
        controls,
    ) { items, assignments, controls ->
        val secretItems = items.filter { it.isSecret && it.space == controls.space }
        val tagsByMediaId = assignments
            .filter { assignment -> secretItems.any { it.id == assignment.mediaId } }
            .groupBy { it.mediaId }
            .mapValues { (_, tags) -> tags.map { it.label }.distinct() }

        HiddenVaultUiState(
            selectedSpace = controls.space,
            items = secretItems,
            workCount = items.count { it.isSecret && it.space == MediaSpace.WORK },
            personalCount = items.count { it.isSecret && it.space == MediaSpace.PERSONAL },
            tagsByMediaId = tagsByMediaId,
            isUnlocked = controls.isUnlocked,
            hasAuthCredential = controls.hasAuthCredential,
            canUseBiometric = controls.canUseBiometric,
            errorMessage = controls.errorMessage,
            infoMessage = controls.infoMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HiddenVaultUiState(),
    )

    fun selectSpace(space: MediaSpace) {
        controls.update { it.copy(space = space) }
    }

    fun unlockByBiometric() {
        controls.update { it.copy(isUnlocked = true, errorMessage = null) }
    }

    fun unlockByPin(pin: String) {
        viewModelScope.launch {
            val ok = authRepository.verifyPin(pin)
            controls.update {
                it.copy(
                    isUnlocked = ok,
                    errorMessage = if (ok) null else "PIN이 올바르지 않습니다.",
                )
            }
        }
    }

    fun restoreItems(items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val uniqueItems = items.distinctBy { it.id }
            uniqueItems.forEach { item ->
                mediaRepository.updateMedia(item.copy(isSecret = false))
            }
            controls.update {
                it.copy(
                    infoMessage = "${uniqueItems.size}개 항목을 일반 목록으로 복원했습니다.",
                    errorMessage = null,
                )
            }
        }
    }

    fun consumeMessages() {
        controls.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}

data class HiddenVaultUiState(
    val selectedSpace: MediaSpace = MediaSpace.WORK,
    val items: List<MediaItemEntity> = emptyList(),
    val workCount: Int = 0,
    val personalCount: Int = 0,
    val tagsByMediaId: Map<Long, List<String>> = emptyMap(),
    val isUnlocked: Boolean = false,
    val hasAuthCredential: Boolean = false,
    val canUseBiometric: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

private data class HiddenVaultControls(
    val space: MediaSpace = MediaSpace.WORK,
    val isUnlocked: Boolean = false,
    val hasAuthCredential: Boolean = false,
    val canUseBiometric: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
