package com.jasonkang.memorix.feature.work.timeline

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
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.designsystem.theme.MemorixPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val sectionFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")

private data class TimelineSection(
    val dateLabel: String,
    val groups: List<TimelineGroup>,
)

data class TimelineGroup(
    val key: String,
    val items: List<MediaItemEntity>,
)

@Composable
fun WorkTimeline(
    items: List<MediaItemEntity>,
    modifier: Modifier = Modifier,
    onItemClick: (MediaItemEntity) -> Unit = {},
) {
    val sections = buildSections(items)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sections.forEach { section ->
            item(key = "header_${section.dateLabel}") {
                TimelineDateHeader(label = section.dateLabel, count = section.groups.sumOf { it.items.size })
            }
            items(section.groups, key = { it.key }) { group ->
                TimelineCard(
                    items = group.items,
                    onClick = onItemClick,
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .background(MemorixPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
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

private fun buildSections(items: List<MediaItemEntity>): List<TimelineSection> {
    if (items.isEmpty()) return emptyList()

    val sorted = items.sortedByDescending { it.takenAt }
    val byDate = sorted.groupBy { item ->
        Instant.ofEpochMilli(item.takenAt)
            .atZone(ZoneId.systemDefault())
            .format(sectionFormatter)
    }

    return byDate.map { (dateLabel, dateItems) ->
        val groups = dateItems
            .groupBy { groupKey(it) }
            .map { (key, groupItems) -> TimelineGroup(key = key, items = groupItems) }
        TimelineSection(dateLabel = dateLabel, groups = groups)
    }
}

private fun groupKey(item: MediaItemEntity): String =
    "${item.takenAt / 3_600_000}_${item.note.hashCode()}_${item.region}"
