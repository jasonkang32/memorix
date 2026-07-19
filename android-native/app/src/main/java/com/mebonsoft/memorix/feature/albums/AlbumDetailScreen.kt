package com.mebonsoft.memorix.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mebonsoft.memorix.feature.home.component.MediaGrid

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onMediaClick: (Long) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val album = uiState.album ?: return
    var showEditDialog by remember(album.id, album.title, album.memo) { mutableStateOf(false) }

    BackHandler { onBack() }

    if (showEditDialog) {
        AlbumEditDialog(
            initialTitle = album.title,
            initialMemo = album.memo,
            title = "앨범 편집",
            onDismiss = { showEditDialog = false },
            onConfirm = { title, memo ->
                viewModel.updateAlbum(title, memo)
                showEditDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(album.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = if (album.memo.isBlank()) "메모 없음" else album.memo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "미디어 ${uiState.items.size}개",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { showEditDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("앨범 편집")
            }
            OutlinedButton(
                onClick = {
                    viewModel.deleteAlbum()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("앨범 삭제")
            }
        }
        if (uiState.items.isEmpty()) {
            Text(
                text = "이 앨범에는 아직 미디어가 없습니다. 홈에서 가져온 뒤 상세화면에서 앨범을 지정하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MediaGrid(
                items = uiState.items,
                modifier = Modifier.weight(1f),
                onItemClick = { item -> onMediaClick(item.id) },
            )
        }
    }
}
