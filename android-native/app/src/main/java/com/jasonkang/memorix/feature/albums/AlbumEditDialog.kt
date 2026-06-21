package com.jasonkang.memorix.feature.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AlbumEditDialog(
    initialTitle: String = "",
    initialMemo: String = "",
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, memo: String) -> Unit,
) {
    var albumTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var albumMemo by remember(initialMemo) { mutableStateOf(initialMemo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = albumTitle,
                    onValueChange = { albumTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("앨범 제목") },
                )
                OutlinedTextField(
                    value = albumMemo,
                    onValueChange = { albumMemo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("메모") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(albumTitle, albumMemo) }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                Text("취소")
            }
        },
    )
}
