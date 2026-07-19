package com.mebonsoft.memorix.feature.work.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimarySoft
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val sectionFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")

internal data class TimelineSection(
    val dateLabel: String,
    val groups: List<TimelineGroup>,
)

data class TimelineGroup(
    val key: String,
    val items: List<MediaItemEntity>,
)

enum class TimelineSortMode(val label: String) {
    REGISTRATION_TIME("등록일시"),
    EVENT_TIME("이벤트 시간"),
}

@Composable
fun WorkTimeline(
    items: List<MediaItemEntity>,
    tagsByMediaId: Map<Long, List<String>>,
    modifier: Modifier = Modifier,
    sortMode: TimelineSortMode = TimelineSortMode.REGISTRATION_TIME,
    onItemClick: (MediaItemEntity) -> Unit = {},
    onSecretClick: (List<MediaItemEntity>) -> Unit = {},
    secretActionContentDescription: String = "목록에서 숨기기",
    secretActionUnlocked: Boolean = false,
) {
    val sections = buildSections(items, sortMode)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sections.forEach { section ->
            item(key = "header_${section.dateLabel}") {
                TimelineDateHeader(label = section.dateLabel, count = section.groups.sumOf { it.items.size })
            }
            items(section.groups, key = { group -> timelineGroupLazyKey(section, group) }) { group ->
                TimelineCard(
                    items = group.items,
                    tags = group.items.flatMap { tagsByMediaId[it.id].orEmpty() }.distinct(),
                    onClick = onItemClick,
                    onSecretClick = { onSecretClick(group.items) },
                    secretActionContentDescription = secretActionContentDescription,
                    secretActionUnlocked = secretActionUnlocked,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineDateHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MemorixInk,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .background(MemorixPrimarySoft, shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MemorixPrimary,
            )
        }
    }
}

internal fun buildSections(
    items: List<MediaItemEntity>,
    sortMode: TimelineSortMode = TimelineSortMode.REGISTRATION_TIME,
): List<TimelineSection> {
    if (items.isEmpty()) return emptyList()

    val groups = items
        .groupBy { groupKey(it) }
        .map { (key, groupItems) ->
            TimelineGroup(
                key = key,
                items = groupItems.sortedWith(itemComparator(sortMode)),
            )
        }
        .sortedWith(groupComparator(sortMode))

    return groups
        .groupBy { group ->
            Instant.ofEpochMilli(group.sectionEpochMillis(sortMode))
                .atZone(ZoneId.systemDefault())
                .format(sectionFormatter)
        }
        .map { (dateLabel, dateGroups) ->
            TimelineSection(dateLabel = dateLabel, groups = dateGroups)
        }
}

private fun groupKey(item: MediaItemEntity): String =
    item.batchGroupId.ifBlank { "${item.takenAt / 3_600_000}_${item.note.hashCode()}_${item.region}" }

private fun itemComparator(sortMode: TimelineSortMode): Comparator<MediaItemEntity> = when (sortMode) {
    TimelineSortMode.REGISTRATION_TIME -> compareByDescending<MediaItemEntity> { it.createdAt }
        .thenByDescending { it.id }
        .thenByDescending { it.takenAt }
    TimelineSortMode.EVENT_TIME -> compareByDescending<MediaItemEntity> { it.takenAt }
        .thenByDescending { it.createdAt }
        .thenByDescending { it.id }
}

private fun groupComparator(sortMode: TimelineSortMode): Comparator<TimelineGroup> = when (sortMode) {
    TimelineSortMode.REGISTRATION_TIME -> compareByDescending<TimelineGroup> { group -> group.items.maxOf { it.createdAt } }
        .thenByDescending { group -> group.items.maxOf { it.id } }
        .thenByDescending { group -> group.items.maxOf { it.takenAt } }
    TimelineSortMode.EVENT_TIME -> compareByDescending<TimelineGroup> { group -> group.items.maxOf { it.takenAt } }
        .thenByDescending { group -> group.items.maxOf { it.createdAt } }
        .thenByDescending { group -> group.items.maxOf { it.id } }
}

private fun TimelineGroup.sectionEpochMillis(sortMode: TimelineSortMode): Long = when (sortMode) {
    TimelineSortMode.REGISTRATION_TIME -> items.maxOf { it.createdAt }
    TimelineSortMode.EVENT_TIME -> items.maxOf { it.takenAt }
}

internal fun timelineGroupLazyKey(section: TimelineSection, group: TimelineGroup): String =
    "${section.dateLabel}_${group.key}"
