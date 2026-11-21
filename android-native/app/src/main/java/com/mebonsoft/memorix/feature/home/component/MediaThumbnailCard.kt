package com.mebonsoft.memorix.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaType
import java.io.File

@Composable
fun MediaThumbnailCard(
    item: MediaItemEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val previewFile = File(item.thumbPath ?: item.filePath)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (item.isLocked) {
            // 잠금 항목은 목록에서 미리보기를 가린다. 열면 상세 화면에서 인증을 요구한다.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = "잠긴 기록", tint = MaterialTheme.colorScheme.primary)
            }
            return@Box
        }

        AsyncImage(
            model = previewFile,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        when (item.mediaType) {
            MediaType.VIDEO -> {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(4.dp),
                )
            }
            MediaType.DOCUMENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE5ECEFF3)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> Unit
        }

        if (item.isArchived || item.isFavorite) {
            Icon(
                imageVector = Icons.Rounded.CloudUpload,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
    }
}
