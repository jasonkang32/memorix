package com.jasonkang.memorix.feature.home

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasonkang.memorix.core.database.entity.MediaItemEntity
import com.jasonkang.memorix.core.database.entity.MediaSpace
import com.jasonkang.memorix.core.database.entity.MediaType

import com.jasonkang.memorix.core.designsystem.theme.MemorixBorderDark
import com.jasonkang.memorix.core.designsystem.theme.MemorixBorderLight
import com.jasonkang.memorix.core.designsystem.theme.MemorixCardDark
import com.jasonkang.memorix.core.designsystem.theme.MemorixCardLight
import com.jasonkang.memorix.core.designsystem.theme.MemorixPersonalEnd
import com.jasonkang.memorix.core.designsystem.theme.MemorixPersonalStart
import com.jasonkang.memorix.core.designsystem.theme.MemorixPrimary
import com.jasonkang.memorix.core.designsystem.theme.MemorixSecondary
import com.jasonkang.memorix.core.designsystem.theme.MemorixSurfaceDark
import com.jasonkang.memorix.core.designsystem.theme.MemorixSurfaceLight
import com.jasonkang.memorix.core.designsystem.theme.MemorixWarning
import com.jasonkang.memorix.core.designsystem.theme.MemorixWorkEnd
import com.jasonkang.memorix.core.designsystem.theme.MemorixWorkStart
import com.jasonkang.memorix.core.media.CameraCaptureSupport
import com.jasonkang.memorix.core.media.DuplicateConfidence
import com.jasonkang.memorix.core.media.ImportDateRange
import com.jasonkang.memorix.core.media.ImportPreview
import com.jasonkang.memorix.core.media.PendingCameraCapture
import java.io.File
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDateRangeUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCameraCapture by remember { mutableStateOf<PendingCameraCapture?>(null) }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        viewModel.previewImport(uris)
    }
    val dateRangePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        pendingDateRangeUris = uris
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

    val summary = rememberHomeSummary(uiState.items)
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val borderColor = if (isDark) MemorixBorderDark else MemorixBorderLight
    val cardColor = if (isDark) MemorixCardDark else MemorixCardLight
    val backgroundColor = if (isDark) MemorixSurfaceDark else MemorixSurfaceLight

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
                        subtitle = "앨범 ${summary.albumEstimate}개 · 인물 0명",
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
                    isImporting = uiState.isImporting,
                    onPickMedia = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onPickDateRange = {
                        dateRangePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onOpenCamera = {
                        val capture = createPendingCameraCapture(context)
                        pendingCameraCapture = capture
                        cameraLauncher.launch(capture.outputUri)
                    },
                    onOpenDocument = {
                        documentLauncher.launch("*/*")
                    },
                )
            }
        }

        if (uiState.isImporting) {
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

    if (pendingDateRangeUris.isNotEmpty()) {
        val dateRangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pendingDateRangeUris = emptyList() },
            confirmButton = {
                TextButton(
                    enabled = dateRangeState.selectedStartDateMillis != null && dateRangeState.selectedEndDateMillis != null,
                    onClick = {
                        val selectedUris = pendingDateRangeUris
                        val range = selectedDateRange(dateRangeState.selectedStartDateMillis, dateRangeState.selectedEndDateMillis)
                        pendingDateRangeUris = emptyList()
                        if (range != null) {
                            viewModel.previewImport(uris = selectedUris, dateRange = range)
                        }
                    },
                ) {
                    Text("미리보기")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDateRangeUris = emptyList() }) {
                    Text("취소")
                }
            },
        ) {
            DateRangePicker(state = dateRangeState)
        }
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
    val dateText = remember {
        DateTimeFormatter.ofPattern("M월 d일 (E)").format(LocalDate.now())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(Brush.linearGradient(listOf(MemorixPrimary, MemorixSecondary)))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Memorix",
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = dateText,
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.clickable(onClick = onSearch),
                )
            }
            Text(
                text = "기억은 빠르게, 보관은 조용하게.",
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }
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
        Text("등록 수", fontSize = 11.sp, color = Color.Gray)
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
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "빠른 가져오기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    label = if (isImporting) "가져오는 중..." else "사진/영상",
                    icon = Icons.Rounded.PhotoLibrary,
                    onClick = onPickMedia,
                    enabled = !isImporting,
                    filled = true,
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    label = "날짜 범위",
                    icon = Icons.Outlined.DateRange,
                    onClick = onPickDateRange,
                    enabled = !isImporting,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    label = "카메라",
                    icon = Icons.Outlined.PhotoCamera,
                    onClick = onOpenCamera,
                    enabled = !isImporting,
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    label = "문서",
                    icon = Icons.Outlined.Description,
                    onClick = onOpenDocument,
                    enabled = !isImporting,
                )
            }
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
                    com.jasonkang.memorix.feature.home.component.MediaThumbnailCard(
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
private fun StorageChip(label: String, count: Int, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text("$label $count", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
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

private data class HomeSummary(
    val totalCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val documentCount: Int,
    val storageLabel: String,
    val albumEstimate: Int,
    val workCount: Int,
    val personalCount: Int,
    val workPhotoCount: Int,
    val workVideoCount: Int,
    val personalPhotoCount: Int,
    val personalVideoCount: Int,
)

@Composable
private fun rememberHomeSummary(items: List<MediaItemEntity>): HomeSummary {
    return remember(items) {
        val photoCount = items.count { it.mediaType == MediaType.PHOTO }
        val videoCount = items.count { it.mediaType == MediaType.VIDEO }
        val documentCount = items.count { it.mediaType == MediaType.DOCUMENT }
        val workItems = items.filter { it.space == MediaSpace.WORK }
        val personalItems = items.filter { it.space == MediaSpace.PERSONAL }
        val totalKb = items.sumOf { it.fileSizeKb }
        val albumEstimate = items.map { Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1) }
            .distinct()
            .size
        HomeSummary(
            totalCount = items.size,
            photoCount = photoCount,
            videoCount = videoCount,
            documentCount = documentCount,
            storageLabel = formatStorage(totalKb),
            albumEstimate = albumEstimate,
            workCount = workItems.size,
            personalCount = personalItems.size,
            workPhotoCount = workItems.count { it.mediaType == MediaType.PHOTO },
            workVideoCount = workItems.count { it.mediaType == MediaType.VIDEO },
            personalPhotoCount = personalItems.count { it.mediaType == MediaType.PHOTO },
            personalVideoCount = personalItems.count { it.mediaType == MediaType.VIDEO },
        )
    }
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
                    com.jasonkang.memorix.feature.home.component.MediaThumbnailCard(
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

private fun formatStorage(totalKb: Long): String {
    val decimal = DecimalFormat("0.#")
    val mb = totalKb / 1024.0
    return if (mb >= 1024) {
        "${decimal.format(mb / 1024.0)}GB"
    } else {
        "${decimal.format(mb)}MB"
    }
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
                    Text("날짜 범위: ${range.startDate} ~ ${range.endDate}")
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

private fun selectedDateRange(startMillis: Long?, endMillis: Long?): ImportDateRange? {
    if (startMillis == null || endMillis == null) return null
    val zoneId = ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
    val endDate = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalDate()
    return ImportDateRange(startDate = startDate, endDate = endDate)
}

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
