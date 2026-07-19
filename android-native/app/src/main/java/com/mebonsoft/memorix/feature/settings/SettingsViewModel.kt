package com.mebonsoft.memorix.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.database.dao.TagDao
import com.mebonsoft.memorix.core.locale.AppLanguage
import com.mebonsoft.memorix.core.locale.LocaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tagDao: TagDao,
    private val localeRepository: LocaleRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        tagDao.observeManagedTags().map { summaries ->
            TagManagementSupport.sortedRows(
                summaries.map { summary ->
                    ManagedTag(
                        id = summary.id,
                        label = summary.label,
                        usageCount = summary.usageCount,
                    )
                }
            )
        },
        localeRepository.language,
    ) { tags, language ->
        SettingsUiState(
            managedTags = tags,
            selectedLanguage = language,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            tagDao.deleteManagedTag(tagId)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            localeRepository.setLanguage(language)
        }
    }
}

data class SettingsUiState(
    val managedTags: List<ManagedTagRow> = emptyList(),
    val selectedLanguage: AppLanguage = AppLanguage.KOREAN,
)
