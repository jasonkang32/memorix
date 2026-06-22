package com.jasonkang.memorix.feature.work

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasonkang.memorix.core.database.entity.MediaType
import com.jasonkang.memorix.core.designsystem.theme.MemorixBorderLight
import com.jasonkang.memorix.core.designsystem.theme.MemorixPrimary
import com.jasonkang.memorix.core.designsystem.theme.MemorixWorkEnd
import com.jasonkang.memorix.core.designsystem.theme.MemorixWorkStart
import com.jasonkang.memorix.core.media.CameraCaptureSupport
import com.jasonkang.memorix.core.media.PendingCameraCapture
import com.jasonkang.memorix.feature.work.compose.PendingMediaHolder
import com.jasonkang.memorix.feature.work.timeline.WorkTimeline
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkScreen(
    onMediaClick: (Long) -> Unit,
    onNavigateToCompose: () -> Unit,
    viewModel: WorkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var showRegisterSheet by remember { mutableStateOf(false) }
    var pendingCameraCapture by remember { mutableStateOf<PendingCameraCapture?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        if (uris.isNotEmpty()) {
            PendingMediaHolder.set(uris)
            onNavigateToCompose()
        }
    }
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            PendingMediaHolder.set(uris)
            onNavigateToCompose()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uris = CameraCaptureSupport.resolveCapturedUris(success, pendingCameraCapture)
        if (!success) pendingCameraCapture?.outputFile?.delete()
        pendingCameraCapture = null
        if (uris.isNotEmpty()) {
            PendingMediaHolder.set(uris)
            onNavigateToCompose()
        }
    }

    if (showRegisterSheet) {
        WorkRegisterDialog(
            onDismiss = { showRegisterSheet = false },
            onPickMedia = {
                showRegisterSheet = false
                mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onOpenCamera = {
                showRegisterSheet = false
                val capture = createWorkPendingCameraCapture(context)
                pendingCameraCapture = capture
                cameraLauncher.launch(capture.outputUri)
            },
            onOpenDocument = {
                showRegisterSheet = false
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
        WorkTopBar(
            modifier = Modifier.padding(top = 12.dp),
            filterActive = uiState.selectedMediaType != null,
            onSearch = { searching = true },
            onAddMedia = { showRegisterSheet = true },
        )

        if (searching) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Work 검색...") },
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

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkMediaFilterChip("전체", uiState.selectedMediaType == null) { viewModel.updateMediaType(null) }
            WorkMediaFilterChip("사진", uiState.selectedMediaType == MediaType.PHOTO) { viewModel.updateMediaType(MediaType.PHOTO) }
            WorkMediaFilterChip("영상", uiState.selectedMediaType == MediaType.VIDEO) { viewModel.updateMediaType(MediaType.VIDEO) }
            WorkMediaFilterChip("문서", uiState.selectedMediaType == MediaType.DOCUMENT) { viewModel.updateMediaType(MediaType.DOCUMENT) }
        }

        if (uiState.filteredItems.isEmpty()) {
            EmptyWorkBlock(hasQuery = uiState.query.isNotBlank())
        } else {
            WorkTimeline(
                items = uiState.filteredItems,
                modifier = Modifier.weight(1f),
                onItemClick = { item -> onMediaClick(item.id) },
            )
        }
    }
}

@Composable
private fun WorkTopBar(
    modifier: Modifier = Modifier,
    filterActive: Boolean,
    onSearch: () -> Unit,
    onAddMedia: () -> Unit,
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
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "검색")
            }
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = "보고서 생성")
            }
            Box {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Tune, contentDescription = "필터")
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
                Icon(Icons.Outlined.AddAPhoto, contentDescription = "미디어 추가")
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
            AssistChipDefaults.assistChipColors(
                containerColor = MemorixPrimary.copy(alpha = 0.16f),
                labelColor = MemorixPrimary,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
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

@Composable
private fun WorkRegisterDialog(
    onDismiss: () -> Unit,
    onPickMedia: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Work 등록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("업무 사진·영상·문서를 Memorix 내부 저장소에 복사해 등록합니다.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("카메라로 촬영")
                }
                Button(onClick = onPickMedia, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Photo, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("사진·영상 가져오기")
                }
                Button(onClick = onOpenDocument, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("문서 가져오기")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) { Text("닫기") }
        },
    )
}

private fun createWorkPendingCameraCapture(context: Context): PendingCameraCapture {
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
