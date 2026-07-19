package com.mebonsoft.memorix.feature.work

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
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.designsystem.theme.MemorixBorder
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimarySoft
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkDeep
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkEnd
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkSoft
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkStart
import com.mebonsoft.memorix.core.media.CameraCaptureSupport
import com.mebonsoft.memorix.core.media.PendingCameraCapture
import com.mebonsoft.memorix.feature.auth.rememberBiometricLoginLauncher
import com.mebonsoft.memorix.feature.search.SearchSupport
import com.mebonsoft.memorix.feature.work.timeline.TimelineSortMode
import com.mebonsoft.memorix.feature.work.timeline.WorkTimeline
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkScreen(
    onMediaClick: (Long) -> Unit,
    onNavigateToCompose: (List<Uri>) -> Unit,
    viewModel: WorkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showRegisterSheet by remember { mutableStateOf(false) }
    var showSecretUnlockDialog by remember { mutableStateOf(false) }
    var secretPinInput by remember { mutableStateOf("") }
    var pendingCameraCapture by remember { mutableStateOf<PendingCameraCapture?>(null) }
    val launchSecretBiometric = rememberBiometricLoginLauncher(
        onSuccess = {
            viewModel.unlockSecretsByBiometric()
            showSecretUnlockDialog = false
            secretPinInput = ""
        },
        onError = { message -> viewModel.consumeImportMessages() },
    )

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onNavigateToCompose(uris)
        }
    }
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onNavigateToCompose(uris)
        }
    }
    LaunchedEffect(uiState.secretUnlocked) {
        if (uiState.secretUnlocked) {
            showSecretUnlockDialog = false
            secretPinInput = ""
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uris = CameraCaptureSupport.resolveCapturedUris(success, pendingCameraCapture)
        if (!success) pendingCameraCapture?.outputFile?.delete()
        pendingCameraCapture = null
        if (uris.isNotEmpty()) {
            onNavigateToCompose(uris)
        }
    }

    if (showSecretUnlockDialog) {
        SecretUnlockDialog(
            pin = secretPinInput,
            hasAuthCredential = uiState.hasAuthCredential,
            canUseBiometric = uiState.canUseBiometric,
            errorMessage = uiState.errorMessage,
            onPinChange = { input -> secretPinInput = input.filter { it.isDigit() }.take(6) },
            onUnlockPin = { viewModel.unlockSecretsByPin(secretPinInput) },
            onUnlockBiometric = launchSecretBiometric,
            onDismiss = {
                showSecretUnlockDialog = false
                secretPinInput = ""
            },
        )
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
            filterActive = uiState.selectedMediaType != null || uiState.selectedTagLabel != null,
            onSearch = { searching = true },
            onFilter = { showFilterDialog = true },
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

        ActiveFilterSummary(
            selectedMediaType = uiState.selectedMediaType,
            selectedTagLabel = uiState.selectedTagLabel,
            onOpenFilter = { showFilterDialog = true },
            onClearMediaType = { viewModel.updateMediaType(null) },
            onClearTag = { viewModel.updateTagFilter(null) },
        )

        TimelineSortRow(
            sortMode = uiState.sortMode,
            onSelect = viewModel::updateSortMode,
        )

        if (uiState.filteredItems.isEmpty()) {
            EmptyWorkBlock(hasQuery = uiState.query.isNotBlank())
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

    if (showFilterDialog) {
        WorkFilterDialog(
            selectedMediaType = uiState.selectedMediaType,
            topTags = uiState.topTags,
            selectedTagLabel = uiState.selectedTagLabel,
            onSelectMediaType = viewModel::updateMediaType,
            onSelectTag = viewModel::updateTagFilter,
            onClear = {
                viewModel.updateMediaType(null)
                viewModel.updateTagFilter(null)
            },
            onDismiss = { showFilterDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineSortRow(
    sortMode: TimelineSortMode,
    onSelect: (TimelineSortMode) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineSortMode.entries.forEach { mode ->
            AssistChip(
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
                colors = if (sortMode == mode) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MemorixPrimary.copy(alpha = 0.16f),
                        labelColor = MemorixPrimary,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@Composable
private fun WorkTopBar(
    modifier: Modifier = Modifier,
    filterActive: Boolean,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onAddMedia: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MemorixBorder),
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
                        .background(MemorixWorkSoft, shape = RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "Work",
                        color = MemorixWorkDeep,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "검색", tint = MemorixInk)
                }
                Box {
                    IconButton(onClick = onFilter) {
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
                text = "업무 사진·영상·문서를 빠르게 찾고, 안전하게 로컬에 정리합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MemorixMuted,
            )
        }
    }
}


private data class WorkTagFilterGroup(
    val title: String,
    val labels: List<String>,
)

private val workTagFilterGroups = listOf(
    WorkTagFilterGroup("기록 종류", listOf("회의", "요청", "결정", "아이디어", "자료")),
    WorkTagFilterGroup("업무 맥락", listOf("내부", "거래처", "현장", "비용", "계약")),
    WorkTagFilterGroup("보관 이유", listOf("참고용", "공유용", "확인용", "증빙용")),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterSummary(
    selectedMediaType: MediaType?,
    selectedTagLabel: String?,
    onOpenFilter: () -> Unit,
    onClearMediaType: () -> Unit,
    onClearTag: () -> Unit,
) {
    if (selectedMediaType == null && selectedTagLabel == null) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        selectedMediaType?.let { mediaType ->
            WorkTagFilterChip(
                label = "유형: ${SearchSupport.mediaTypeLabel(mediaType)} ×",
                selected = true,
                onClick = onClearMediaType,
            )
        }
        selectedTagLabel?.let { tag ->
            WorkTagFilterChip(
                label = "#$tag ×",
                selected = true,
                onClick = onClearTag,
            )
        }
        AssistChip(
            onClick = onOpenFilter,
            label = { Text("필터 변경") },
            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkFilterDialog(
    selectedMediaType: MediaType?,
    topTags: List<TagUsageSummary>,
    selectedTagLabel: String?,
    onSelectMediaType: (MediaType?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Work 필터", fontWeight = FontWeight.Bold, color = MemorixInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "필요할 때만 열어서 유형과 태그로 Work 기록을 좁혀봅니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MemorixMuted,
                )
                Text("미디어 유형", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MemorixInk)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkMediaFilterChip("전체", selectedMediaType == null) { onSelectMediaType(null) }
                    WorkMediaFilterChip("사진", selectedMediaType == MediaType.PHOTO) { onSelectMediaType(MediaType.PHOTO) }
                    WorkMediaFilterChip("영상", selectedMediaType == MediaType.VIDEO) { onSelectMediaType(MediaType.VIDEO) }
                    WorkMediaFilterChip("문서", selectedMediaType == MediaType.DOCUMENT) { onSelectMediaType(MediaType.DOCUMENT) }
                }
                WorkTagFilterPanel(
                    topTags = topTags,
                    selectedTagLabel = selectedTagLabel,
                    onSelect = onSelectTag,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("적용") } },
        dismissButton = { TextButton(onClick = onClear) { Text("초기화") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkTagFilterPanel(
    topTags: List<TagUsageSummary>,
    selectedTagLabel: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (topTags.isNotEmpty()) {
            Text("자주 쓴 태그", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MemorixInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topTags.forEach { tag ->
                    WorkTagFilterChip(
                        label = "#${tag.label}",
                        selected = selectedTagLabel == tag.label,
                        onClick = { onSelect(tag.label) },
                    )
                }
            }
        }
        workTagFilterGroups.forEach { group ->
            Text(group.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MemorixInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.labels.forEach { label ->
                    WorkTagFilterChip(
                        label = label,
                        selected = selectedTagLabel == label,
                        onClick = { onSelect(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkTagFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) {
            AssistChipDefaults.assistChipColors(
                containerColor = MemorixPrimary.copy(alpha = 0.14f),
                labelColor = MemorixPrimary,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
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
                containerColor = MemorixWorkSoft,
                labelColor = MemorixWorkDeep,
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MemorixBorder.copy(alpha = 0.8f)),
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
private fun SecretUnlockDialog(
    pin: String,
    hasAuthCredential: Boolean,
    canUseBiometric: Boolean,
    errorMessage: String?,
    onPinChange: (String) -> Unit,
    onUnlockPin: () -> Unit,
    onUnlockBiometric: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("비밀 Work 보기", fontWeight = FontWeight.Bold, color = MemorixInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (hasAuthCredential) {
                        "비밀로 설정한 Work 항목은 평소 목록에서 숨겨집니다. PIN 또는 생체인증으로 잠시 표시할 수 있습니다."
                    } else {
                        "비밀 Work를 사용하려면 먼저 설정에서 앱 잠금 PIN 또는 생체인증을 설정해 주세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MemorixMuted,
                )
                if (hasAuthCredential) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = onPinChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIN 6자리") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(12.dp),
                    )
                    errorMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (canUseBiometric) {
                        Button(
                            onClick = onUnlockBiometric,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MemorixPrimary, contentColor = Color.White),
                        ) {
                            Icon(Icons.Outlined.LockOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("생체인증으로 보기")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasAuthCredential) {
                TextButton(onClick = onUnlockPin, enabled = pin.length == 6) { Text("보기") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
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
