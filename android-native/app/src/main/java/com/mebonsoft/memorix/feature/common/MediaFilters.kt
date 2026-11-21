package com.mebonsoft.memorix.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkDeep
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkSoft
import com.mebonsoft.memorix.feature.search.SearchSupport
import com.mebonsoft.memorix.feature.work.timeline.TimelineSortMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimelineSortRow(
    sortMode: TimelineSortMode,
    onSelect: (TimelineSortMode) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineSortMode.entries.forEach { mode ->
            AssistChip(
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
                colors = if (sortMode == mode) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MemorixPrimary.copy(alpha = 0.16f),
                        labelColor = MemorixPrimary,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

private data class TagFilterGroup(
    val title: String,
    val labels: List<String>,
)

private val tagFilterGroups = listOf(
    TagFilterGroup("기록 종류", listOf("회의", "요청", "결정", "아이디어", "자료")),
    TagFilterGroup("업무 맥락", listOf("내부", "거래처", "현장", "비용", "계약")),
    TagFilterGroup("보관 이유", listOf("참고용", "공유용", "확인용", "증빙용")),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveFilterSummary(
    selectedMediaType: MediaType?,
    selectedTagLabel: String?,
    onOpenFilter: () -> Unit,
    onClearMediaType: () -> Unit,
    onClearTag: () -> Unit,
) {
    if (selectedMediaType == null && selectedTagLabel == null) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        selectedMediaType?.let { mediaType ->
            TagFilterChip(
                label = "유형: ${SearchSupport.mediaTypeLabel(mediaType)} ×",
                selected = true,
                onClick = onClearMediaType,
            )
        }
        selectedTagLabel?.let { tag ->
            TagFilterChip(
                label = "#$tag ×",
                selected = true,
                onClick = onClearTag,
            )
        }
        AssistChip(
            onClick = onOpenFilter,
            label = { Text("필터 변경") },
            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaFilterDialog(
    spaceLabel: String,
    selectedMediaType: MediaType?,
    topTags: List<TagUsageSummary>,
    selectedTagLabel: String?,
    onSelectMediaType: (MediaType?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$spaceLabel 미디어 필터", fontWeight = FontWeight.Bold, color = MemorixInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "필요할 때만 열어서 유형과 태그로 $spaceLabel 기록을 좁혀봅니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MemorixMuted,
                )
                Text("미디어 유형", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MemorixInk)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaFilterChip("전체", selectedMediaType == null) { onSelectMediaType(null) }
                    MediaFilterChip("사진", selectedMediaType == MediaType.PHOTO) { onSelectMediaType(MediaType.PHOTO) }
                    MediaFilterChip("영상", selectedMediaType == MediaType.VIDEO) { onSelectMediaType(MediaType.VIDEO) }
                    MediaFilterChip("문서", selectedMediaType == MediaType.DOCUMENT) { onSelectMediaType(MediaType.DOCUMENT) }
                }
                TagFilterPanel(
                    topTags = topTags,
                    selectedTagLabel = selectedTagLabel,
                    onSelect = onSelectTag,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("적용") } },
        dismissButton = { TextButton(onClick = onClear) { Text("초기화") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFilterPanel(
    topTags: List<TagUsageSummary>,
    selectedTagLabel: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (topTags.isNotEmpty()) {
            Text("자주 쓴 태그", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MemorixInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topTags.forEach { tag ->
                    TagFilterChip(
                        label = "#${tag.label}",
                        selected = selectedTagLabel == tag.label,
                        onClick = { onSelect(tag.label) },
                    )
                }
            }
        }
        tagFilterGroups.forEach { group ->
            Text(group.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MemorixInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.labels.forEach { label ->
                    TagFilterChip(
                        label = label,
                        selected = selectedTagLabel == label,
                        onClick = { onSelect(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) {
            AssistChipDefaults.assistChipColors(
                containerColor = MemorixPrimary.copy(alpha = 0.14f),
                labelColor = MemorixPrimary,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}

@Composable
private fun MediaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) {
            AssistChipDefaults.assistChipColors(
                containerColor = MemorixWorkSoft,
                labelColor = MemorixWorkDeep,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}

