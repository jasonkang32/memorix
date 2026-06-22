package com.jasonkang.memorix.feature.work.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaType
import com.jasonkang.memorix.core.designsystem.theme.MemorixPrimary
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val headerDateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineCard(
    items: List<MediaItemEntity>,
    onClick: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val representative = items.first()
    val photoCount = items.count { it.mediaType == MediaType.PHOTO }
    val videoCount = items.count { it.mediaType == MediaType.VIDEO }
    var noteExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (representative.region.isNotBlank() || representative.countryCode.isNotBlank()) {
                    Text(
                        text = buildLocationLabel(representative.countryCode, representative.region),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = formatTakenAt(representative.takenAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ImageGrid(
                items = items,
                onClick = onClick,
            )

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (representative.note.isNotBlank()) {
                    Column(modifier = Modifier.animateContentSize()) {
                        Text(
                            text = representative.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (noteExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!noteExpanded && representative.note.length > 60) {
                            Text(
                                text = "더보기",
                                style = MaterialTheme.typography.labelSmall,
                                color = MemorixPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { noteExpanded = true },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (photoCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${photoCount}장",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (videoCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${videoCount}개",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGrid(
    items: List<MediaItemEntity>,
    onClick: (MediaItemEntity) -> Unit,
) {
    val displayItems = items.take(4)
    val remaining = items.size - 3

    when (displayItems.size) {
        1 -> {
            GridImage(
                item = displayItems[0],
                onClick = { onClick(displayItems[0]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            )
        }
        2 -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                displayItems.forEach { item ->
                    GridImage(
                        item = item,
                        onClick = { onClick(item) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
            }
        }
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                GridImage(
                    item = displayItems[0],
                    onClick = { onClick(displayItems[0]) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    GridImage(
                        item = displayItems[1],
                        onClick = { onClick(displayItems[1]) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        GridImage(
                            item = displayItems[2],
                            onClick = { onClick(displayItems[2]) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (remaining > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+$remaining",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridImage(
    item: MediaItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewFile = File(item.thumbPath ?: item.filePath)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = previewFile,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (item.mediaType == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Rounded.PlayCircleOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
            )
        }
    }
}

private fun buildLocationLabel(countryCode: String, region: String): String {
    val flag = countryCodeToFlag(countryCode)
    return buildString {
        if (flag.isNotBlank()) append("$flag ")
        if (region.isNotBlank()) append(region)
        if (region.isBlank() && countryCode.isNotBlank()) append(countryCode)
    }.trim()
}

private fun countryCodeToFlag(code: String): String {
    if (code.length != 2) return ""
    val upper = code.uppercase()
    val first = Character.toChars(0x1F1E6 - 'A'.code + upper[0].code)
    val second = Character.toChars(0x1F1E6 - 'A'.code + upper[1].code)
    return String(first) + String(second)
}

private fun formatTakenAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(headerDateFormatter)
