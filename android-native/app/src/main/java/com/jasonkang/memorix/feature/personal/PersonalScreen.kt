package com.jasonkang.memorix.feature.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasonkang.memorix.core.database.entity.AlbumSummary
import com.jasonkang.memorix.core.designsystem.theme.MemorixPersonalEnd
import com.jasonkang.memorix.core.designsystem.theme.MemorixPersonalStart
import com.jasonkang.memorix.feature.albums.AlbumCard
import com.jasonkang.memorix.feature.albums.AlbumEditDialog
import com.jasonkang.memorix.feature.home.component.MediaGrid

@Composable
fun PersonalScreen(
    onAlbumClick: (Long) -> Unit,
    onMediaClick: (Long) -> Unit,
    viewModel: PersonalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        AlbumEditDialog(
            title = "새 Personal 앨범",
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, memo ->
                viewModel.createAlbum(title, memo)
                showCreateDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonalTopBar(
            isAlbumGridMode = uiState.isAlbumGridMode,
            onToggleAlbumGrid = viewModel::toggleAlbumGrid,
            onCreateAlbum = { showCreateDialog = true },
            onSearch = { searching = true },
            onAddMedia = { },
            modifier = Modifier.padding(top = 12.dp),
        )

        if (searching) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Personal 검색...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.updateQuery("")
                        searching = false
                    }) { Icon(Icons.Outlined.Search, contentDescription = null) }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }

        if (uiState.albums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "앨범 바로가기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.albums, key = { it.id }) { album ->
                        AssistChip(
                            onClick = { onAlbumClick(album.id) },
                            label = { Text(album.title) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Collections, contentDescription = null)
                            },
                        )
                    }
                }
            }
        }

        if (uiState.isAlbumGridMode) {
            if (uiState.albums.isEmpty()) {
                EmptyPersonalAlbumsBlock()
            } else {
                LazyVerticalGrid(
                    modifier = Modifier.weight(1f),
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.albums, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
                    }
                }
            }
        } else {
            if (uiState.filteredItems.isEmpty()) {
                EmptyPersonalTimelineBlock(hasQuery = uiState.query.isNotBlank())
            } else {
                MediaGrid(
                    items = uiState.filteredItems,
                    modifier = Modifier.weight(1f),
                    onItemClick = { item -> onMediaClick(item.id) },
                )
            }
        }
    }
}

@Composable
private fun PersonalTopBar(
    isAlbumGridMode: Boolean,
    onToggleAlbumGrid: () -> Unit,
    onCreateAlbum: () -> Unit,
    onSearch: () -> Unit,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(listOf(MemorixPersonalStart, MemorixPersonalEnd)),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Personal",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "검색")
            }
            IconButton(onClick = onToggleAlbumGrid) {
                Icon(
                    imageVector = if (isAlbumGridMode) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    contentDescription = null,
                )
            }
            IconButton(onClick = onCreateAlbum) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
            }
            if (!isAlbumGridMode) {
                IconButton(onClick = onAddMedia) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = "미디어 추가")
                }
            }
        }
    }
}

@Composable
private fun EmptyPersonalAlbumsBlock() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
                    .background(Brush.linearGradient(listOf(MemorixPersonalStart, MemorixPersonalEnd)), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = "아직 앨범이 없습니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "상단 폴더 버튼으로 여행, 기념일, 가족 앨범을 추가해보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPersonalTimelineBlock(hasQuery: Boolean) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
                    .background(Brush.linearGradient(listOf(MemorixPersonalStart, MemorixPersonalEnd)), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = if (hasQuery) "검색 결과가 없습니다" else "아직 Personal 미디어가 없습니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (hasQuery) {
                    "다른 키워드로 다시 찾아보세요."
                } else {
                    "개인 사진과 영상을 이 공간에 모으면 앨범과 타임라인으로 쉽게 돌아볼 수 있습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
