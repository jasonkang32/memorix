package com.jasonkang.memorix.feature.personal

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.jasonkang.memorix.core.database.entity.AlbumSummary
import com.jasonkang.memorix.core.media.CameraCaptureSupport
import com.jasonkang.memorix.core.media.PendingCameraCapture
import com.jasonkang.memorix.core.designsystem.theme.MemorixPersonalEnd
import com.jasonkang.memorix.core.designsystem.theme.MemorixPersonalStart
import com.jasonkang.memorix.feature.albums.AlbumCard
import com.jasonkang.memorix.feature.albums.AlbumEditDialog
import com.jasonkang.memorix.feature.home.component.MediaGrid
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun PersonalScreen(
    onAlbumClick: (Long) -> Unit,
    onMediaClick: (Long) -> Unit,
    viewModel: PersonalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var pendingCameraCapture by remember { mutableStateOf<PendingCameraCapture?>(null) }
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris -> viewModel.importMedia(uris) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uris = CameraCaptureSupport.resolveCapturedUris(success, pendingCameraCapture)
        if (!success) pendingCameraCapture?.outputFile?.delete()
        pendingCameraCapture = null
        viewModel.importMedia(uris)
    }

    LaunchedEffect(uiState.importMessage, uiState.errorMessage) {
        if (uiState.importMessage != null || uiState.errorMessage != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.consumeImportMessages()
        }
    }

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

    if (showRegisterDialog) {
        PersonalRegisterDialog(
            onDismiss = { showRegisterDialog = false },
            onPickMedia = {
                showRegisterDialog = false
                mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onOpenCamera = {
                showRegisterDialog = false
                val capture = createPersonalPendingCameraCapture(context)
                pendingCameraCapture = capture
                cameraLauncher.launch(capture.outputUri)
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
            onAddMedia = { showRegisterDialog = true },
            modifier = Modifier.padding(top = 12.dp),
        )

        uiState.importMessage?.let { message ->
            PersonalStatusBanner(message = message, isError = false)
        }
        uiState.errorMessage?.let { message ->
            PersonalStatusBanner(message = message, isError = true)
        }
        if (uiState.isImporting) {
            PersonalStatusBanner(message = "등록 중입니다...", isError = false)
        }

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

@Composable
private fun PersonalRegisterDialog(
    onDismiss: () -> Unit,
    onPickMedia: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personal 등록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("개인 사진·영상을 Memorix 내부 저장소에 복사해 등록합니다.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("카메라로 촬영")
                }
                Button(onClick = onPickMedia, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Collections, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("사진·영상 가져오기")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) { Text("닫기") }
        },
    )
}

@Composable
private fun PersonalStatusBanner(message: String, isError: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MemorixPersonalStart.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MemorixPersonalStart,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun createPersonalPendingCameraCapture(context: Context): PendingCameraCapture {
    val outputDir = File(context.cacheDir, "memorix-camera").apply { mkdirs() }
    val fileName = "camera_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}_${UUID.randomUUID()}.jpg"
    val outputFile = File(outputDir, fileName)
    val outputUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
    return PendingCameraCapture(
        outputFile = outputFile,
        outputUri = outputUri,
        authority = "${context.packageName}.fileprovider",
    )
}
