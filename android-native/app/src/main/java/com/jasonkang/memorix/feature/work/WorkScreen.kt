package com.jasonkang.memorix.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasonkang.memorix.core.database.entity.MediaType
import com.jasonkang.memorix.core.designsystem.theme.MemorixBorderLight
import com.jasonkang.memorix.core.designsystem.theme.MemorixPrimary
import com.jasonkang.memorix.core.designsystem.theme.MemorixWarning
import com.jasonkang.memorix.core.designsystem.theme.MemorixWorkEnd
import com.jasonkang.memorix.core.designsystem.theme.MemorixWorkStart
import com.jasonkang.memorix.feature.home.component.MediaGrid

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkScreen(
    onMediaClick: (Long) -> Unit,
    viewModel: WorkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoCount = uiState.items.count { it.mediaType == MediaType.PHOTO }
    val videoCount = uiState.items.count { it.mediaType == MediaType.VIDEO }
    val documentCount = uiState.items.count { it.mediaType == MediaType.DOCUMENT }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkTopBar(modifier = Modifier.padding(top = 12.dp))

        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::updateQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Work 검색") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkMediaFilterChip("전체", uiState.selectedMediaType == null) { viewModel.updateMediaType(null) }
            WorkMediaFilterChip("사진", uiState.selectedMediaType == MediaType.PHOTO) { viewModel.updateMediaType(MediaType.PHOTO) }
            WorkMediaFilterChip("영상", uiState.selectedMediaType == MediaType.VIDEO) { viewModel.updateMediaType(MediaType.VIDEO) }
            WorkMediaFilterChip("문서", uiState.selectedMediaType == MediaType.DOCUMENT) { viewModel.updateMediaType(MediaType.DOCUMENT) }
        }

        Text(
            text = uiState.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorkQuickStat(
                modifier = Modifier.weight(1f),
                label = "사진",
                value = "${photoCount}개",
                icon = Icons.Outlined.Photo,
                tint = MemorixWorkStart,
            )
            WorkQuickStat(
                modifier = Modifier.weight(1f),
                label = "영상",
                value = "${videoCount}개",
                icon = Icons.Outlined.VideoLibrary,
                tint = MemorixPrimary,
            )
            WorkQuickStat(
                modifier = Modifier.weight(1f),
                label = "문서",
                value = "${documentCount}개",
                icon = Icons.Outlined.Description,
                tint = MemorixWarning,
            )
        }

        WorkReportCard()

        Text(
            text = if (uiState.filteredItems.isEmpty()) "최근 업무 보관함" else "업무 타임라인",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        if (uiState.filteredItems.isEmpty()) {
            EmptyWorkBlock(hasQuery = uiState.query.isNotBlank())
        } else {
            MediaGrid(
                items = uiState.filteredItems,
                modifier = Modifier.weight(1f),
                onItemClick = { item -> onMediaClick(item.id) },
            )
        }
    }
}

@Composable
private fun WorkTopBar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(listOf(MemorixWorkStart, MemorixWorkEnd)),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Work",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Tune, contentDescription = null)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(MemorixWorkStart, MemorixWorkEnd)))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Work,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Text(
                            text = "업무 보관함",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Text(
                        text = "현장 사진, 영상, 문서를 타임라인 중심으로 빠르게 정리하는 Work 공간",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkMediaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) {
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                containerColor = MemorixPrimary.copy(alpha = 0.16f),
                labelColor = MemorixPrimary,
            )
        } else {
            androidx.compose.material3.AssistChipDefaults.assistChipColors()
        },
    )
}

@Composable
private fun WorkQuickStat(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkReportCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "보고서 생성",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "출장보고서, 사진대지, 현장 기록용 출력을 준비하는 영역",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MemorixPrimary.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MemorixPrimary)
            }
        }
    }
}

@Composable
private fun EmptyWorkBlock(hasQuery: Boolean) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MemorixBorderLight.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(Brush.linearGradient(listOf(MemorixWorkStart, MemorixWorkEnd)), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Work,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = if (hasQuery) "검색 결과가 없습니다" else "업무 미디어가 없어요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (hasQuery) {
                    "다른 키워드나 유형 필터를 시도해보세요."
                } else {
                    "메모릭스에만 보관하고, 외부에 노출되지 않게 Work 공간에서 관리해보세요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
