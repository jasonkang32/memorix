package com.jasonkang.memorix.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jasonkang.memorix.feature.common.PlaceholderScreen
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val mediaDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    viewModel: MediaDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.item
    if (item == null) {
        PlaceholderScreen(
            title = "미디어 로딩 중",
            subtitle = "선택한 미디어를 불러오는 중입니다.",
            bullets = listOf("잠시만 기다려주세요."),
        )
        return
    }

    var title by remember(item.id, item.title) { mutableStateOf(item.title) }
    var note by remember(item.id, item.note) { mutableStateOf(item.note) }
    var selectedAlbumId by remember(item.id, item.albumId) { mutableLongStateOf(item.albumId ?: 0L) }
    var isFavorite by remember(item.id, item.isFavorite) { mutableStateOf(item.isFavorite) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = File(item.thumbPath ?: item.filePath),
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Text(
            text = Instant.ofEpochMilli(item.takenAt).atZone(ZoneId.systemDefault()).format(mediaDateFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("제목") },
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("메모") },
            minLines = 3,
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = uiState.albums.firstOrNull { it.id == selectedAlbumId }?.title ?: "미분류",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                label = { Text("앨범") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("미분류") },
                    onClick = {
                        selectedAlbumId = 0L
                        expanded = false
                    },
                )
                uiState.albums.forEach { album ->
                    DropdownMenuItem(
                        text = { Text(album.title) },
                        onClick = {
                            selectedAlbumId = album.id
                            expanded = false
                        },
                    )
                }
            }
        }
        Button(
            onClick = {
                viewModel.save(
                    MediaEditorSupport.applyDraft(
                        item = item,
                        title = title,
                        note = note,
                        albumId = selectedAlbumId.takeIf { it != 0L },
                        takenAt = item.takenAt,
                        isFavorite = isFavorite,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("저장")
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
            Text("즐겨찾기", modifier = Modifier.padding(top = 12.dp))
        }
    }
}
