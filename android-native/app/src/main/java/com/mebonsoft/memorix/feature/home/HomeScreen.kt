package com.mebonsoft.memorix.feature.home

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.rememberDatePickerState
import com.mebonsoft.memorix.core.database.dao.TagUsageSummary
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType

import com.mebonsoft.memorix.core.designsystem.theme.MemorixBorder
import com.mebonsoft.memorix.core.designsystem.theme.MemorixBorderDark
import com.mebonsoft.memorix.core.designsystem.theme.MemorixBorderLight
import com.mebonsoft.memorix.core.designsystem.theme.MemorixCardDark
import com.mebonsoft.memorix.core.designsystem.theme.MemorixCardLight
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPersonalEnd
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPersonalStart
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimaryBright
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimarySoft
import com.mebonsoft.memorix.core.designsystem.theme.MemorixSecondary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixSurfaceDark
import com.mebonsoft.memorix.core.designsystem.theme.MemorixSurfaceLight
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWarning
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkEnd
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkStart
import com.mebonsoft.memorix.core.media.CameraCaptureSupport
import com.mebonsoft.memorix.core.media.DuplicateConfidence
import com.mebonsoft.memorix.core.media.ImportPreview
import com.mebonsoft.memorix.core.media.PendingCameraCapture
import java.io.File
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMediaClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onWorkClick: () -> Unit,
    onPersonalClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDayPicker by remember { mutableStateOf(false) }
    var selectedDayMillis by remember { mutableStateOf<Long?>(null) }
    var showDaySpacePicker by remember { mutableStateOf(false) }
    var pendingDayImportRequest by remember { mutableStateOf<MediaStoreDayImportRequest?>(null) }
    var dayImportJob by remember { mutableStateOf<Job?>(null) }
    var isDayBulkImportActive by remember { mutableStateOf(false) }
    var isDayImportScanning by remember { mutableStateOf(false) }
    var pendingCameraCapture by remember { mutableStateOf<PendingCameraCapture?>(null) }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        viewModel.previewImport(uris)
    }
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        viewModel.previewImport(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uris = CameraCaptureSupport.resolveCapturedUris(success, pendingCameraCapture)
        if (!success) {
            pendingCameraCapture?.outputFile?.delete()
        }
        pendingCameraCapture = null
        viewModel.previewImport(uris)
    }
    val launchCameraCapture = {
        runCatching {
            val capture = createPendingCameraCapture(context)
            pendingCameraCapture = capture
            cameraLauncher.launch(capture.outputUri)
        }.onFailure { error ->
            pendingCameraCapture?.outputFile?.delete()
            pendingCameraCapture = null
            viewModel.showError(error.message ?: "카메라를 열 수 없습니다.")
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            viewModel.showError(HomeQuickImportSupport.cameraPermissionDeniedMessage())
        }
    }
    fun openDayPicker() {
        selectedDayMillis = null
        showDaySpacePicker = false
        showDayPicker = true
    }
    fun resetDayImportState() {
        dayImportJob?.cancel()
        dayImportJob = null
        pendingDayImportRequest = null
        selectedDayMillis = null
        showDayPicker = false
        showDaySpacePicker = false
        isDayImportScanning = false
        isDayBulkImportActive = false
        viewModel.cancelImport()
    }
    fun runDayImport(request: MediaStoreDayImportRequest) {
        dayImportJob?.cancel()
        isDayBulkImportActive = true
        dayImportJob = coroutineScope.launch {
            isDayImportScanning = true
            runCatching {
                MediaStoreDayImportSupport.queryVisualMediaUrisForDay(
                    resolver = context.contentResolver,
                    selectedDate = request.selectedDate,
                )
            }.onFailure { error ->
                dayImportJob = null
                isDayImportScanning = false
                if (error !is CancellationException) {
                    isDayBulkImportActive = false
                    viewModel.showError(error.message ?: "해당 날짜의 사진·영상을 찾지 못했습니다.")
                }
            }.onSuccess { uris ->
                dayImportJob = null
                isDayImportScanning = false
                selectedDayMillis = null
                pendingDayImportRequest = null
                if (uris.isEmpty()) {
                    isDayBulkImportActive = false
                    viewModel.showError("선택한 날짜에 가져올 사진·영상이 없습니다.")
                } else {
                    viewModel.importMedia(uris = uris, space = request.space)
                }
            }
        }
    }
    val mediaReadPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val request = pendingDayImportRequest
        if (grants.isNotEmpty() && grants.values.all { it } && request != null) {
            pendingDayImportRequest = null
            runDayImport(request)
        } else {
            pendingDayImportRequest = null
            viewModel.showError(HomeQuickImportSupport.mediaPermissionDeniedMessage())
        }
    }
    fun importDayToSpace(space: MediaSpace) {
        val request = MediaStoreDayImportSupport.dayImportRequest(
            selectedMillis = selectedDayMillis,
            space = space,
        )
        if (request == null) {
            viewModel.showError("가져올 날짜를 선택해주세요.")
            return
        }
        showDaySpacePicker = false
        if (HomeQuickImportSupport.hasMediaReadPermission(context)) {
            runDayImport(request)
        } else {
            val permissions = HomeQuickImportSupport.mediaReadPermissions()
            if (permissions.isEmpty()) {
                runDayImport(request)
            } else {
                pendingDayImportRequest = request
                mediaReadPermissionLauncher.launch(permissions)
            }
        }
    }

    val summary = rememberHomeSummary(uiState.items)
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val borderColor = if (isDark) MemorixBorderDark else MemorixBorderLight
    val cardColor = if (isDark) MemorixCardDark else MemorixCardLight
    val backgroundColor = if (isDark) MemorixSurfaceDark else MemorixSurfaceLight
    val quickImportBusy = uiState.isImporting || isDayImportScanning
    val dayBulkImportBusy = isDayBulkImportActive && quickImportBusy

    LaunchedEffect(dayBulkImportBusy) {
        if (!dayBulkImportBusy) {
            isDayBulkImportActive = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroHeader(onSearch = onSearchClick)
        }

        item {
            PaddedHomeContent {
                SummaryCard(summary = summary, cardColor = cardColor, borderColor = borderColor)
            }
        }

        item {
            PaddedHomeContent {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpaceCard(
                        modifier = Modifier.weight(1f),
                        label = "Work",
                        count = summary.workCount,
                        subtitle = "사진 ${summary.workPhotoCount}개 · 영상 ${summary.workVideoCount}개",
                        gradient = Brush.linearGradient(listOf(MemorixWorkStart, MemorixWorkEnd)),
                        icon = Icons.Outlined.Work,
                        onClick = onWorkClick,
                    )
                    SpaceCard(
                        modifier = Modifier.weight(1f),
                        label = "Personal",
                        count = summary.personalCount,
                        subtitle = "사진 ${summary.personalPhotoCount}개 · 영상 ${summary.personalVideoCount}개",
                        gradient = Brush.linearGradient(listOf(MemorixPersonalStart, MemorixPersonalEnd)),
                        icon = Icons.Outlined.Favorite,
                        onClick = onPersonalClick,
                    )
                }
            }
        }

        item {
            PaddedHomeContent {
                QuickImportSection(
                    isImporting = quickImportBusy,
                    onPickMedia = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onPickDateRange = ::openDayPicker,
                    onOpenCamera = {
                        if (HomeQuickImportSupport.hasCameraPermission(context)) {
                            launchCameraCapture()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onOpenDocument = {
                        documentLauncher.launch("*/*")
                    },
                )
            }
        }

        item {
            PaddedHomeContent { SectionTitle(title = "태그 Top 10") }
        }
        item {
            PaddedHomeContent { TopTagSection(tags = uiState.topTags) }
        }

        if (quickImportBusy && !dayBulkImportBusy) {
            item {
                CircularProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally))
            }
        }

        uiState.importSummaryMessage?.let { message ->
            item {
                StatusBanner(
                    message = message,
                    background = MemorixPrimary.copy(alpha = 0.12f),
                    textColor = MemorixPrimary,
                )
            }
        }

        uiState.errorMessage?.let { message ->
            item {
                StatusBanner(
                    message = message,
                    background = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    textColor = MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            PaddedHomeContent { SectionTitle(title = "최근 등록") }
        }
        if (uiState.items.isEmpty()) {
            item {
                PaddedHomeContent { EmptyState() }
            }
        } else {
            item {
                PaddedHomeContent { RecentRow(items = uiState.items.take(8), onMediaClick = onMediaClick) }
            }
            item {
                PaddedHomeContent { SectionTitle(title = "최근 30일 활동") }
            }
            item {
                PaddedHomeContent { ActivityChart(items = uiState.items) }
            }
            item {
                PaddedHomeContent { SectionTitle(title = "저장 공간") }
            }
            item {
                PaddedHomeContent { StorageUsageCard(summary = summary) }
            }
            item {
                PaddedHomeContent { TypeBreakdown(summary = summary) }
            }
            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    if (showDayPicker) {
        val dayPickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(
                    enabled = dayPickerState.selectedDateMillis != null,
                    onClick = {
                        selectedDayMillis = dayPickerState.selectedDateMillis
                        showDayPicker = false
                        showDaySpacePicker = true
                    },
                ) {
                    Text("다음")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDayPicker = false }) {
                    Text("취소")
                }
            },
        ) {
            DatePicker(state = dayPickerState)
        }
    }

    if (showDaySpacePicker) {
        AlertDialog(
            onDismissRequest = { showDaySpacePicker = false },
            title = { Text("등록 위치 선택") },
            text = { Text("선택한 날짜의 모든 사진·영상을 어디에 등록할까요?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importDayToSpace(MediaSpace.PERSONAL) }) {
                        Text("Personal")
                    }
                    Button(onClick = { importDayToSpace(MediaSpace.WORK) }) {
                        Text("Work")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDaySpacePicker = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (dayBulkImportBusy) {
        DayBulkImportBlockingOverlay(
            isScanning = isDayImportScanning,
            onCancel = ::resetDayImportState,
        )
    }

    uiState.pendingImportPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            selectedSpace = uiState.selectedImportSpace,
            onSelectSpace = viewModel::selectImportSpace,
            onDismiss = viewModel::dismissImportPreview,
            onConfirm = viewModel::confirmImport,
        )
    }
}

@Composable
private fun DayBulkImportBlockingOverlay(
    isScanning: Boolean,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = if (isScanning) "해당 날짜의 사진·영상을 찾는 중입니다" else "일괄 등록 중입니다",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "처리가 끝날 때까지 다른 작업을 할 수 없습니다. 너무 오래 걸리면 중단할 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onCancel) {
                        Text("중단")
                    }
                }
            }
        }
    }
}

@Composable
private fun PaddedHomeContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun HeroHeader(
    onSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .background(
                Brush.linearGradient(
                    listOf(MemorixInk, MemorixPrimary, MemorixPrimaryBright),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(118.dp)
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(74.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "M",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "메모릭스",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp,
                    )
                    Text(
                        text = "memorix",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onSearch)
                        .padding(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "검색",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "기억은 빠르게 보관은 조용하게.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "사진·영상·문서를 폰 안에서 빠르게 정리하는 프리미엄 로컬 보관함",
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickImportTile(
    modifier: Modifier = Modifier,
    action: HomeQuickImportAction,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean = false,
    titleOverride: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val title = titleOverride ?: action.title
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = action.description,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 92.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MemorixPrimary,
                contentColor = Color.White,
                disabledContainerColor = MemorixPrimary.copy(alpha = 0.32f),
            ),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 92.dp),
            shape = shape,
        ) { content() }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(52.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MemorixPrimary,
                contentColor = Color.White,
                disabledContainerColor = MemorixPrimary.copy(alpha = 0.32f),
            ),
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(52.dp),
            shape = shape,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun SummaryCard(
    summary: HomeSummary,
    cardColor: Color,
    borderColor: Color,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor)
            .border(1.dp, borderColor, shape)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TotalSummaryStat(
            modifier = Modifier.weight(1f),
            summary = summary,
        )
        DividerBlock(borderColor = borderColor)
        SummaryStat(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.PhotoLibrary,
            title = "저장 용량",
            value = summary.storageLabel,
            color = MemorixSecondary,
        )
        DividerBlock(borderColor = borderColor)
        SummaryStat(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.CloudUpload,
            title = "Drive 대기",
            value = "0개",
            color = MemorixPrimary,
        )
    }
}

@Composable
private fun DividerBlock(borderColor: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(64.dp)
            .background(borderColor),
    )
}

@Composable
private fun TotalSummaryStat(
    modifier: Modifier = Modifier,
    summary: HomeSummary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Rounded.PermMedia, contentDescription = null, tint = MemorixPrimary, modifier = Modifier.height(22.dp))
        Text(
            "${summary.totalCount}개",
            fontSize = 18.sp,
            color = MemorixPrimary,
            fontWeight = FontWeight.ExtraBold,
        )
        Text("등록 수", fontSize = 11.sp, color = MemorixMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniTypeLabel("사진", summary.photoCount, MemorixWorkStart)
            MiniTypeLabel("영상", summary.videoCount, MemorixPersonalStart)
            if (summary.documentCount > 0) {
                MiniTypeLabel("문서", summary.documentCount, MemorixWarning)
            }
        }
    }
}

@Composable
private fun MiniTypeLabel(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text("$label $count", fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryStat(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    color: Color,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpaceCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    subtitle: String,
    gradient: Brush,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${count}개", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuickImportSection(
    isImporting: Boolean,
    onPickMedia: () -> Unit,
    onPickDateRange: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MemorixBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "빠른 가져오기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MemorixInk,
                    )
                    Text(
                        text = "가져오는 방식에 따라 보관 기준이 달라집니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MemorixMuted,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MemorixPrimarySoft)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "로컬 저장",
                        style = MaterialTheme.typography.labelSmall,
                        color = MemorixPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickImportTile(
                    modifier = Modifier.weight(1f),
                    action = HomeQuickImportAction.PHOTO_VIDEO,
                    icon = Icons.Rounded.PhotoLibrary,
                    onClick = onPickMedia,
                    enabled = !isImporting,
                    filled = true,
                    titleOverride = if (isImporting) "가져오는 중..." else null,
                )
                QuickImportTile(
                    modifier = Modifier.weight(1f),
                    action = HomeQuickImportAction.DATE_RANGE,
                    icon = Icons.Outlined.DateRange,
                    onClick = onPickDateRange,
                    enabled = !isImporting,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickImportTile(
                    modifier = Modifier.weight(1f),
                    action = HomeQuickImportAction.CAMERA,
                    icon = Icons.Outlined.PhotoCamera,
                    onClick = onOpenCamera,
                    enabled = !isImporting,
                )
                QuickImportTile(
                    modifier = Modifier.weight(1f),
                    action = HomeQuickImportAction.DOCUMENT,
                    icon = Icons.Outlined.Description,
                    onClick = onOpenDocument,
                    enabled = !isImporting,
                )
            }
        }
    }
}

@Composable
private fun BackupRestoreSection(
    totalBytes: Long,
    mediaBytes: Long,
    databaseBytes: Long,
    isWorking: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MemorixBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "백업 · 복구",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MemorixInk,
                    )
                    Text(
                        text = "현재 앱 관리 전체 용량 ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MemorixMuted,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MemorixPrimarySoft)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = formatBytes(totalBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MemorixPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageChip("미디어", count = 0, color = MemorixWorkStart, textOverride = "미디어 ${formatBytes(mediaBytes)}")
                StorageChip("DB", count = 0, color = MemorixPersonalStart, textOverride = "DB ${formatBytes(databaseBytes)}")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    label = if (isWorking) "처리 중..." else "백업",
                    icon = Icons.Rounded.CloudUpload,
                    onClick = onBackup,
                    enabled = !isWorking,
                    filled = true,
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    label = "복구",
                    icon = Icons.Rounded.PermMedia,
                    onClick = onRestore,
                    enabled = !isWorking,
                )
            }
            Text(
                text = "백업 파일에는 DB와 앱 내부 originals/thumbs 파일이 함께 저장됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MemorixMuted,
            )
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    background: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(message, color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TopTagSection(tags: List<TagUsageSummary>) {
    if (tags.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        ) {
            Text(
                text = "아직 태그 사용 기록이 없습니다. Work 상세에서 태그를 선택하거나 추가하면 Top 10이 표시됩니다.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.take(10).forEachIndexed { index, tag ->
            TopTagChip(rank = index + 1, tag = tag)
        }
    }
}

@Composable
private fun TopTagChip(rank: Int, tag: TagUsageSummary) {
    val accent = remember(tag.colorHex) { parseTagColor(tag.colorHex) }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "#${tag.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MemorixInk,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${tag.usageCount}회",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun parseTagColor(colorHex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(colorHex))
}.getOrDefault(MemorixPrimary)

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
    ) {
        Text(
            text = "아직 가져온 미디어가 없습니다. 사진/영상, 카메라, 문서 가져오기로 보관함을 채워보세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentRow(
    items: List<MediaItemEntity>,
    onMediaClick: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier.width(96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                ) {
                    com.mebonsoft.memorix.feature.home.component.MediaThumbnailCard(
                        item = item,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { onMediaClick(item.id) },
                    )
                }
                Text(
                    text = item.title.ifBlank { displayTypeLabel(item.mediaType) },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

@Composable
private fun ActivityChart(items: List<MediaItemEntity>) {
    val dailyCounts = remember(items) {
        val today = LocalDate.now()
        val counts = items.groupingBy {
            Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.eachCount()
        (29 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            counts[date] ?: 0
        }
    }
    val maxCount = dailyCounts.maxOrNull()?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dailyCounts.forEach { count ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((count.toFloat() / maxCount) * 90).coerceAtLeast(if (count > 0) 8f else 2f).dp)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(if (count > 0) MemorixPrimary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun StorageUsageCard(summary: HomeSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("전체 용량", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(summary.storageLabel, color = MemorixSecondary, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageChip("사진", summary.photoCount, MemorixWorkStart)
                StorageChip("영상", summary.videoCount, MemorixPersonalStart)
                StorageChip("문서", summary.documentCount, MemorixWarning)
            }
        }
    }
}

@Composable
private fun TypeBreakdown(summary: HomeSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BreakdownCard(
            modifier = Modifier.weight(1f),
            title = "Work",
            total = summary.workCount,
            photo = summary.workPhotoCount,
            video = summary.workVideoCount,
            gradient = Brush.linearGradient(listOf(MemorixWorkStart, MemorixWorkEnd)),
        )
        BreakdownCard(
            modifier = Modifier.weight(1f),
            title = "Personal",
            total = summary.personalCount,
            photo = summary.personalPhotoCount,
            video = summary.personalVideoCount,
            gradient = Brush.linearGradient(listOf(MemorixPersonalStart, MemorixPersonalEnd)),
        )
    }
}

@Composable
private fun StorageChip(label: String, count: Int, color: Color, textOverride: String? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(textOverride ?: "$label $count", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BreakdownCard(
    modifier: Modifier = Modifier,
    title: String,
    total: Int,
    photo: Int,
    video: Int,
    gradient: Brush,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text("${total}개", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("사진 $photo · 영상 $video", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun rememberHomeSummary(items: List<MediaItemEntity>): HomeSummary = remember(items) {
    calculateHomeSummary(items)
}

@Composable
private fun LibraryGridRow(
    items: List<MediaItemEntity>,
    onMediaClick: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) { index ->
            val item = items.getOrNull(index)
            if (item == null) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                ) {
                    com.mebonsoft.memorix.feature.home.component.MediaThumbnailCard(
                        item = item,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { onMediaClick(item.id) },
                    )
                }
            }
        }
    }
}

private fun groupLibraryRows(items: List<MediaItemEntity>): List<List<MediaItemEntity>> =
    items.sortedByDescending { it.takenAt }.chunked(3)

private fun formatBytes(bytes: Long): String {
    val decimal = DecimalFormat("0.#")
    if (bytes <= 0L) return "0B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "${decimal.format(gb)}GB"
        mb >= 1 -> "${decimal.format(mb)}MB"
        kb >= 1 -> "${decimal.format(kb)}KB"
        else -> "${bytes}B"
    }
}

private fun backupFileName(): String {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now())
    return "Memorix_Backup_$stamp.zip"
}

private fun displayTypeLabel(type: MediaType): String = when (type) {
    MediaType.PHOTO -> "사진"
    MediaType.VIDEO -> "영상"
    MediaType.DOCUMENT -> "문서"
}

private fun displaySpaceLabel(space: MediaSpace): String = when (space) {
    MediaSpace.WORK -> "Work"
    MediaSpace.PERSONAL -> "Personal"
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    selectedSpace: MediaSpace,
    onSelectSpace: (MediaSpace) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("가져오기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        title = { Text("가져오기 미리보기") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("선택 항목 ${preview.items.size}개")
                Text(
                    text = "저장 위치를 선택하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selectedSpace == MediaSpace.WORK) {
                        Button(
                            onClick = { onSelectSpace(MediaSpace.WORK) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Work")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectSpace(MediaSpace.WORK) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Work")
                        }
                    }

                    if (selectedSpace == MediaSpace.PERSONAL) {
                        Button(
                            onClick = { onSelectSpace(MediaSpace.PERSONAL) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Personal")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectSpace(MediaSpace.PERSONAL) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Personal")
                        }
                    }
                }
                Text(
                    text = "현재 저장 위치: ${displaySpaceLabel(selectedSpace)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preview.dateRange?.let { range ->
                    Text(
                        if (range.dayCount == 1L) {
                            "선택 날짜: ${range.startDate}"
                        } else {
                            "날짜 범위: ${range.startDate} ~ ${range.endDate}"
                        }
                    )
                }
                if (preview.filteredOutCount > 0) {
                    Text(
                        text = "범위 밖 항목 ${preview.filteredOutCount}개 제외",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.duplicateItemCount > 0) {
                    Text(
                        text = "중복 의심 항목 ${preview.duplicateItemCount}개",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    preview.items.forEach { item ->
                        val strongest = item.duplicateMatches.firstOrNull()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = buildString {
                                    append(item.mediaType.name)
                                    item.takenAtEpochMillis?.let {
                                        append(" · ")
                                        append(formatTakenAt(it))
                                    }
                                    if (item.fileSizeKb > 0) {
                                        append(" · ")
                                        append("${item.fileSizeKb}KB")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            strongest?.let { match ->
                                Text(
                                    text = when (match.confidence) {
                                        DuplicateConfidence.EXACT -> "동일 항목 의심: ${match.probe.displayName}"
                                        DuplicateConfidence.HIGH -> "강한 중복 의심: ${match.probe.displayName}"
                                        DuplicateConfidence.LOW -> "중복 가능성 있음: ${match.probe.displayName}"
                                        DuplicateConfidence.NONE -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (match.confidence == DuplicateConfidence.HIGH) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun formatTakenAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))

private fun createPendingCameraCapture(context: Context): PendingCameraCapture {
    val outputDir = File(context.cacheDir, "memorix-camera").apply { mkdirs() }
    val fileName = "camera_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}_${UUID.randomUUID()}.jpg"
    val outputFile = File(outputDir, fileName)
    val outputUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outputFile,
    )
    return PendingCameraCapture(
        outputFile = outputFile,
        outputUri = outputUri,
        authority = "${context.packageName}.fileprovider",
    )
}
