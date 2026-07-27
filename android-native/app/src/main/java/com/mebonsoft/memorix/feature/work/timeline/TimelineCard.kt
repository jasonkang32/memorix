package com.mebonsoft.memorix.feature.work.timeline

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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.designsystem.theme.MemorixBorder
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val headerDateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm")

private val workListDateTextSize = 14.3.sp
private val workListMetaTextSize = 14.3.sp
private val workListBodyTextSize = 15.6.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineCard(
    items: List<MediaItemEntity>,
    tags: List<String>,
    onClick: (MediaItemEntity) -> Unit,
    onSecretClick: () -> Unit,
    secretActionContentDescription: String = "목록에서 숨기기",
    secretActionUnlocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val representative = items.first()
    val photoCount = items.count { it.mediaType == MediaType.PHOTO }
    val videoCount = items.count { it.mediaType == MediaType.VIDEO }
    val documentCount = items.count { it.mediaType == MediaType.DOCUMENT }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MemorixBorder),
    ) {
        Column {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (representative.region.isNotBlank() || representative.countryCode.isNotBlank()) {
                    Text(
                        text = buildLocationLabel(representative.countryCode, representative.region),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = workListBodyTextSize),
                        fontWeight = FontWeight.SemiBold,
                        color = MemorixInk,
                    )
                }
                Text(
                    text = formatTakenAt(representative.takenAt),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = workListDateTextSize),
                    color = MemorixMuted,
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
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                modifier = Modifier
                                    .background(MemorixPrimary.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = workListMetaTextSize,
                                color = MemorixPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (representative.note.isNotBlank()) {
                    Text(
                        text = representative.note,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = workListBodyTextSize),
                        color = MemorixInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (photoCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MemorixMuted,
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "${photoCount}장",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = workListMetaTextSize),
                                    color = MemorixMuted,
                                )
                            }
                        }
                        if (videoCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MemorixMuted,
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "${videoCount}개",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = workListMetaTextSize),
                                    color = MemorixMuted,
                                )
                            }
                        }
                        if (documentCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MemorixMuted,
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "문서 ${documentCount}개",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = workListMetaTextSize),
                                    color = MemorixMuted,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onSecretClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (secretActionUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = secretActionContentDescription,
                            tint = MemorixMuted,
                            modifier = Modifier.size(18.dp),
                        )
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
        if (item.mediaType == MediaType.DOCUMENT) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MemorixPrimary,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = "문서",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MemorixMuted,
                )
            }
        } else {
            AsyncImage(
                model = previewFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
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
