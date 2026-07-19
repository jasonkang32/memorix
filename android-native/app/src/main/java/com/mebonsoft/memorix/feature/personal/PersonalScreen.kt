package com.mebonsoft.memorix.feature.personal

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.GridView

import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.mebonsoft.memorix.core.database.entity.AlbumSummary
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.media.CameraCaptureSupport
import com.mebonsoft.memorix.core.media.PendingCameraCapture
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPersonalEnd
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPersonalStart
import com.mebonsoft.memorix.core.designsystem.theme.MemorixBorder
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimarySoft
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkDeep
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkEnd
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkSoft
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkStart
import com.mebonsoft.memorix.feature.albums.AlbumCard
import com.mebonsoft.memorix.feature.albums.AlbumEditDialog
import com.mebonsoft.memorix.feature.work.timeline.TimelineSortMode
import com.mebonsoft.memorix.feature.work.timeline.WorkTimeline
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalScreen(
    onAlbumClick: (Long) -> Unit,
    onMediaClick: (Long) -> Unit,
    onNavigateToCompose: (List<Uri>) -> Unit,
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
    ) { uris -> if (uris.isNotEmpty()) onNavigateToCompose(uris) }
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> if (uris.isNotEmpty()) onNavigateToCompose(uris) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uris = CameraCaptureSupport.resolveCapturedUris(success, pendingCameraCapture)
        if (!success) pendingCameraCapture?.outputFile?.delete()
        pendingCameraCapture = null
        if (uris.isNotEmpty()) onNavigateToCompose(uris)
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
            onOpenDocument = {
                showRegisterDialog = false
                documentLauncher.launch("*/*")
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
            modifier = Modifier.padding(top = 12.dp),
            filterActive = uiState.selectedMediaType != null,
            onSearch = { searching = true },
            onAddMedia = { showRegisterDialog = true },
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
                    }) { Icon(Icons.Outlined.Tune, contentDescription = null) }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }

        PersonalMediaFilterRow(
            selectedMediaType = uiState.selectedMediaType,
            onSelect = viewModel::updateMediaType,
        )

        PersonalTimelineSortRow(
            sortMode = uiState.sortMode,
            onSelect = viewModel::updateSortMode,
        )

        if (uiState.filteredItems.isEmpty()) {
            EmptyPersonalTimelineBlock(hasQuery = uiState.query.isNotBlank())
        } else {
            WorkTimeline(
                items = uiState.filteredItems,
                tagsByMediaId = uiState.tagsByMediaId,
                modifier = Modifier.weight(1f),
                sortMode = uiState.sortMode,
                onItemClick = { item -> onMediaClick(item.id) },
                onSecretClick = viewModel::hideItems,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalTimelineSortRow(
    sortMode: TimelineSortMode,
    onSelect: (TimelineSortMode) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineSortMode.entries.forEach { mode ->
            PersonalMediaFilterChip(mode.label, sortMode == mode) { onSelect(mode) }
        }
    }
}

@Composable
private fun PersonalTopBar(
    modifier: Modifier = Modifier,
    filterActive: Boolean,
    onSearch: () -> Unit,
    onAddMedia: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MemorixBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(MemorixPersonalStart.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "Personal",
                        color = MemorixPersonalEnd,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "검색", tint = MemorixInk)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = "보고서 생성", tint = MemorixMuted)
                }
                Box {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Tune, contentDescription = "필터", tint = if (filterActive) MemorixPrimary else MemorixMuted)
                    }
                    if (filterActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(8.dp)
                                .background(MemorixPrimary, shape = androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
                IconButton(onClick = onAddMedia) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = "미디어 추가", tint = MemorixPrimary)
                }
            }
            Text(
                text = "가족·여행·일상의 순간을 감정과 장소 중심으로 조용히 보관합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MemorixMuted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalMediaFilterRow(
    selectedMediaType: MediaType?,
    onSelect: (MediaType?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PersonalMediaFilterChip("전체", selectedMediaType == null) { onSelect(null) }
        PersonalMediaFilterChip("사진", selectedMediaType == MediaType.PHOTO) { onSelect(MediaType.PHOTO) }
        PersonalMediaFilterChip("영상", selectedMediaType == MediaType.VIDEO) { onSelect(MediaType.VIDEO) }
        PersonalMediaFilterChip("문서", selectedMediaType == MediaType.DOCUMENT) { onSelect(MediaType.DOCUMENT) }
    }
}

@Composable
private fun PersonalMediaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) {
            AssistChipDefaults.assistChipColors(
                containerColor = MemorixPersonalStart.copy(alpha = 0.14f),
                labelColor = MemorixPersonalEnd,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
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

@Composable
private fun PersonalRegisterDialog(
    onDismiss: () -> Unit,
    onPickMedia: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Work 등록", fontWeight = FontWeight.Bold, color = MemorixInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "업무 사진·영상·문서를 Memorix 내부 저장소에 복사해 등록합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MemorixMuted,
                )
                Button(
                    onClick = onOpenCamera,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MemorixPrimary, contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("카메라로 촬영")
                }
                Button(
                    onClick = onPickMedia,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MemorixPrimarySoft, contentColor = MemorixPrimary),
                ) {
                    Icon(Icons.Outlined.Photo, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("사진·영상 가져오기")
                }
                Button(
                    onClick = onOpenDocument,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MemorixWorkSoft, contentColor = MemorixWorkDeep),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("문서 가져오기")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MemorixInk),
            ) { Text("닫기") }
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
