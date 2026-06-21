package com.jasonkang.memorix.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private data class MediaSection(
    val label: String,
    val items: List<MediaItemEntity>,
)

private val sectionFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")

@Composable
fun MediaGrid(
    items: List<MediaItemEntity>,
    modifier: Modifier = Modifier,
    onItemClick: (MediaItemEntity) -> Unit = {},
) {
    val sections = rememberSections(items)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sections, key = { it.label }) { section ->
            SectionHeader(label = section.label, count = section.items.size)
            section.items.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(3) { index ->
                        val item = rowItems.getOrNull(index)
                        if (item == null) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            MediaThumbnailCard(
                                item = item,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
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
                .background(MemorixPrimary.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
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

private fun rememberSections(items: List<MediaItemEntity>): List<MediaSection> {
    if (items.isEmpty()) return emptyList()
    return items
        .sortedByDescending { it.takenAt }
        .groupBy {
            Instant.ofEpochMilli(it.takenAt)
                .atZone(ZoneId.systemDefault())
                .format(sectionFormatter)
        }
        .map { (label, groupedItems) -> MediaSection(label = label, items = groupedItems) }
}
