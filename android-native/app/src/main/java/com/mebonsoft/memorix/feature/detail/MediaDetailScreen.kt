package com.mebonsoft.memorix.feature.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.database.entity.TagEntity
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.monetization.ProEntitlement
import com.mebonsoft.memorix.core.monetization.ProFeature
import com.mebonsoft.memorix.core.monetization.ProUpgradeContent
import com.mebonsoft.memorix.feature.common.PlaceholderScreen
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val detailDateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)  HH:mm")
private val metaDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
    onBack: () -> Unit,
    viewModel: MediaDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.item
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
    var countryCode by remember(item.id, item.countryCode) { mutableStateOf(item.countryCode) }
    var region by remember(item.id, item.region) { mutableStateOf(item.region) }
    var isSecret by remember(item.id, item.isSecret) { mutableStateOf(item.isSecret) }
    var takenAt by remember(item.id, item.takenAt) { mutableLongStateOf(item.takenAt) }
    var tagInput by remember(item.id) { mutableStateOf("") }
    var selectedTagIds by remember(item.id) { mutableStateOf(uiState.selectedTagIds) }
    var tagsDirty by remember(item.id) { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddMediaDialog by remember { mutableStateOf(false) }
    var showOcrProDialog by remember { mutableStateOf(false) }
    var shouldCloseAfterSave by remember { mutableStateOf(false) }
    var mediaPendingRemoval by remember { mutableStateOf<MediaItemEntity?>(null) }
    var fullscreenPreviewItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    var pendingProFeature by remember { mutableStateOf<ProFeature?>(null) }
    val isPro = uiState.entitlement == ProEntitlement.ProLifetime

    LaunchedEffect(uiState.selectedTagIds) {
        if (!tagsDirty) selectedTagIds = uiState.selectedTagIds
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }
    LaunchedEffect(shouldCloseAfterSave, uiState.isSaving, uiState.message) {
        if (shouldCloseAfterSave && !uiState.isSaving && uiState.message == "저장했습니다.") {
            shouldCloseAfterSave = false
            onBack()
        }
    }
    LaunchedEffect(item.id, uiState.relatedItems, item.countryCode, item.region) {
        if (item.countryCode.isBlank() && item.region.isBlank()) {
            val firstPhoto = uiState.relatedItems.firstOrNull { it.mediaType == MediaType.PHOTO } ?: item.takeIf { it.mediaType == MediaType.PHOTO }
            firstPhoto?.let { photo ->
                viewModel.autoFillLocation(photo.filePath) { location ->
                    location?.let {
                        countryCode = it.countryCode
                        region = it.region
                    }
                }
            }
        }
    }

    val isDirty = note.trim() != item.note.trim() ||
        countryCode.trim() != item.countryCode.trim() ||
        region.trim() != item.region.trim() ||
        isSecret != item.isSecret ||
        takenAt != item.takenAt ||
        selectedTagIds.toSet() != uiState.selectedTagIds.toSet()

    fun saveCurrentDraft() {
        viewModel.saveDraft(
            item = item,
            title = item.title,
            note = note,
            countryCode = countryCode,
            region = region,
            takenAt = takenAt,
            selectedTagIds = selectedTagIds,
            isSecret = isSecret,
            relatedItems = uiState.relatedItems,
        )
        tagsDirty = false
    }

    fun requestBack() {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    BackHandler(onBack = ::requestBack)

    val addPhotoVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris: List<Uri> ->
        viewModel.addMediaToItem(
            uris = uris,
            item = item,
            countryCode = countryCode,
            region = region,
        )
    }
    val addFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris: List<Uri> ->
        viewModel.addMediaToItem(
            uris = uris,
            item = item,
            countryCode = countryCode,
            region = region,
        )
    }

    val spaceLabel = detailSpaceLabel(item.space)
    val detailTitle = "$spaceLabel 미디어"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(detailTitle) },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !uiState.isSaving,
                        onClick = ::saveCurrentDraft,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = "저장")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.relatedItems.size > 1) {
                items(uiState.relatedItems, key = { "related_${it.id}" }) { relatedItem ->
                    val order = uiState.relatedItems.indexOfFirst { it.id == relatedItem.id } + 1
                    MediaPreview(
                        item = relatedItem,
                        orderLabel = "$order/${uiState.relatedItems.size}",
                        onFullscreenClick = { fullscreenPreviewItem = relatedItem },
                        onRemove = { mediaPendingRemoval = relatedItem },
                    )
                }
            } else {
                item {
                    MediaPreview(
                        item = item,
                        orderLabel = null,
                        onFullscreenClick = { fullscreenPreviewItem = item },
                    )
                }
            }
            item { AddMediaButton(onClick = { showAddMediaDialog = true }) }
            if (uiState.relatedItems.size > 1) {
                item {
                    ExportActionsSection(
                        isWorking = uiState.isSaving,
                        onPdfExport = {
                            if (!isPro) {
                                pendingProFeature = ProFeature.PdfExport
                            } else {
                                coroutineScope.launch {
                                    val file = withContext(Dispatchers.IO) { createPhotoPdfExport(context, uiState.relatedItems) }
                                    if (file != null) shareExportFile(context, file, "application/pdf", "PDF 내보내기")
                                    else snackbarHostState.showSnackbar("PDF로 내보낼 사진을 찾지 못했습니다")
                                }
                            }
                        },
                        onBatchShare = {
                            if (!isPro) {
                                pendingProFeature = ProFeature.BatchShare
                            } else {
                                coroutineScope.launch {
                                    val file = withContext(Dispatchers.IO) { createBatchZipExport(context, uiState.relatedItems) }
                                    if (file != null) shareExportFile(context, file, "application/zip", "묶음 공유")
                                    else snackbarHostState.showSnackbar("묶음 공유할 파일을 찾지 못했습니다")
                                }
                            }
                        },
                    )
                }
            }
            item { MediaMeta(item = item, countryCode = countryCode, region = region) }
            item {
                MemoField(
                    note = note,
                    onNoteChange = { note = it },
                )
            }
            item {
                SecretWorkRow(
                    spaceLabel = spaceLabel,
                    isSecret = isSecret,
                    onCheckedChange = { isSecret = it },
                )
            }
            item {
                EventDateRow(
                    takenAt = takenAt,
                    onClick = { showDatePicker = true },
                )
            }
            item {
                WorkLocationFields(
                    countryCode = countryCode,
                    region = region,
                    isLocating = uiState.isLocating,
                    onCountryChange = { countryCode = it },
                    onRegionChange = { region = it },
                )
            }

            if (item.mediaType != MediaType.VIDEO) {
                item {
                    OcrSection(
                        item = item,
                        isRunning = uiState.isOcrRunning,
                        isPro = isPro,
                        onRun = { if (isPro) viewModel.runOcr(item) else showOcrProDialog = true },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OCR", item.ocrText))
                            coroutineScope.launch { snackbarHostState.showSnackbar("클립보드에 복사했습니다") }
                        },
                    )
                }
            }
            item {
                TagSection(
                    allTags = uiState.availableTags,
                    selectedTagIds = selectedTagIds,
                    tagInput = tagInput,
                    onTagInputChange = { tagInput = it },
                    onToggle = { tagId ->
                        selectedTagIds = if (tagId in selectedTagIds) selectedTagIds - tagId else selectedTagIds + tagId
                        tagsDirty = true
                    },
                    onAddCustom = {
                        viewModel.addCustomTag(tagInput, selectedTagIds)
                        tagInput = ""
                        tagsDirty = false
                    },
                )
            }
            item {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("$spaceLabel 미디어 삭제", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showOcrProDialog) {
        val copy = ProUpgradeContent.forFeature(ProFeature.OcrSearch)
        AlertDialog(
            onDismissRequest = { showOcrProDialog = false },
            title = { Text(copy.title, fontWeight = FontWeight.Bold) },
            text = { Text(copy.body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showOcrProDialog = false }) { Text("확인") }
            },
        )
    }

    pendingProFeature?.let { feature ->
        val copy = ProUpgradeContent.forFeature(feature)
        AlertDialog(
            onDismissRequest = { pendingProFeature = null },
            title = { Text(copy.title, fontWeight = FontWeight.Bold) },
            text = { Text(copy.body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { pendingProFeature = null }) { Text("확인") }
            },
        )
    }

    fullscreenPreviewItem?.let { previewItem ->
        val previewItems = fullscreenPreviewItems(
            selectedItem = previewItem,
            relatedItems = uiState.relatedItems,
        )
        FullscreenMediaPreviewDialog(
            items = previewItems,
            initialPage = fullscreenInitialPage(
                selectedItemId = previewItem.id,
                previewItems = previewItems,
            ),
            onDismiss = { fullscreenPreviewItem = null },
            onShare = { shareItem ->
                val shared = shareMediaItem(context, shareItem)
                if (!shared) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("공유할 수 있는 사진 파일을 찾지 못했습니다") }
                }
            },
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = takenAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { selected ->
                            takenAt = mergeDateKeepingTime(currentEpochMillis = takenAt, selectedDateMillis = selected)
                        }
                        showDatePicker = false
                    },
                ) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showAddMediaDialog) {
        AlertDialog(
            onDismissRequest = { showAddMediaDialog = false },
            title = { Text("미디어 추가") },
            text = { Text("사진/영상 또는 파일만 이 상세 항목에 추가합니다. 메모와 기록 태그는 새로 추가하지 않습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddMediaDialog = false
                        addPhotoVideoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                ) { Text("사진/영상") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showAddMediaDialog = false
                            addFileLauncher.launch("*/*")
                        },
                    ) { Text("파일") }
                    TextButton(onClick = { showAddMediaDialog = false }) { Text("취소") }
                }
            },
        )
    }

    mediaPendingRemoval?.let { removingItem ->
        AlertDialog(
            onDismissRequest = { mediaPendingRemoval = null },
            title = { Text("미디어 제거") },
            text = { Text("이 사진/영상/파일을 현재 상세 묶음에서 제거할까요? 앱 보관함에서도 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        mediaPendingRemoval = null
                        viewModel.removeMediaFromGroup(
                            item = removingItem,
                            closeDetail = removingItem.id == item.id,
                        )
                    },
                ) { Text("제거", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { mediaPendingRemoval = null }) { Text("취소") }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("수정 내용을 저장할까요?") },
            text = { Text("뒤로 가기 전에 변경한 내용을 저장할 수 있습니다.") },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSaving,
                    onClick = {
                        showDiscardDialog = false
                        shouldCloseAfterSave = true
                        saveCurrentDraft()
                    },
                ) { Text("저장") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onBack) { Text("저장 안 함") }
                    TextButton(onClick = { showDiscardDialog = false }) { Text("계속 수정") }
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("$spaceLabel 미디어 삭제") },
            text = { Text("이 $spaceLabel 미디어 전체를 삭제할까요? 묶음에 포함된 사진/영상/파일도 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMedia(item, uiState.relatedItems)
                    },
                ) { Text("삭제", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun FullscreenMediaPreviewDialog(
    items: List<MediaItemEntity>,
    initialPage: Int,
    onDismiss: () -> Unit,
    onShare: (MediaItemEntity) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { items.size }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) { page ->
                val item = items[page]
                Box(modifier = Modifier.fillMaxSize()) {
                    if (item.mediaType == MediaType.VIDEO) {
                        VideoFullscreenPlayer(item = item)
                    } else {
                        ZoomableFullscreenImage(item = item)
                    }
                }
            }
            val currentItem = items[pagerState.currentPage]
            Text(
                text = "${pagerState.currentPage + 1}/${items.size}",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = { onShare(currentItem) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 72.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.58f), CircleShape),
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = "사진 공유",
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.58f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "크게 보기 닫기",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun VideoFullscreenPlayer(item: MediaItemEntity) {
    val context = LocalContext.current
    val player = remember(item.id, item.filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(File(item.filePath).toUri()))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ZoomableFullscreenImage(item: MediaItemEntity) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = File(item.filePath),
        contentDescription = item.title.ifBlank { "이미지 크게 보기" },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val shouldHandleZoomGesture = pressedCount >= 2
                        val shouldHandlePanGesture = scale > 1f
                        if (shouldHandleZoomGesture || shouldHandlePanGesture) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = nextScale
                            offset = if (nextScale == 1f) {
                                Offset.Zero
                            } else {
                                offset + panChange
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun MediaPreview(
    item: MediaItemEntity,
    orderLabel: String?,
    onFullscreenClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    if (item.mediaType == MediaType.DOCUMENT) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF5F5F5),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(File(item.filePath).name, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            onRemove?.let { RemoveMediaButton(onClick = it) }
        }
        return
    }

    val canOpenFullscreen = isFullscreenPreviewAvailable(item.mediaType) && onFullscreenClick != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFECEFF3))
            .then(if (canOpenFullscreen) Modifier.clickable(onClick = onFullscreenClick!!) else Modifier),
    ) {
        AsyncImage(
            model = File(item.thumbPath ?: item.filePath),
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        orderLabel?.let {
            Text(
                text = it,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (item.mediaType == MediaType.VIDEO) {
            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.76f), modifier = Modifier.align(Alignment.Center).size(52.dp))
        }
        onRemove?.let { RemoveMediaButton(onClick = it) }
    }
}

@Composable
private fun BoxScope.RemoveMediaButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.54f), CircleShape),
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "미디어 제거",
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun AddMediaButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
        border = BorderStroke(1.dp, Color(0xFFD6DAE0)),
    ) {
        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text("미디어 추가")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportActionsSection(
    isWorking: Boolean,
    onPdfExport: () -> Unit,
    onBatchShare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Pro 내보내기")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onPdfExport, enabled = !isWorking, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text("PDF 내보내기", fontSize = 13.sp)
            }
            OutlinedButton(onClick = onBatchShare, enabled = !isWorking, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text("묶음 ZIP 공유", fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaMeta(item: MediaItemEntity, countryCode: String, region: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetaItem(icon = Icons.Outlined.Folder, text = formatFileSize(File(item.filePath).length().takeIf { it > 0 } ?: (item.fileSizeKb * 1024)))
        val location = listOf(countryCode, region).filter { it.isNotBlank() }.joinToString(" · ")
        if (location.isNotBlank()) MetaItem(icon = Icons.Outlined.LocationOn, text = location)
        MetaItem(icon = Icons.Outlined.Event, text = Instant.ofEpochMilli(item.takenAt).atZone(ZoneId.systemDefault()).format(metaDateFormatter))
    }
}

@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(text, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun MemoField(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("메모")
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("메모") },
            placeholder = { Text("메모를 입력하세요") },
            minLines = 4,
        )
    }
}

@Composable
private fun SecretWorkRow(
    spaceLabel: String,
    isSecret: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSecret) MemorixPrimary.copy(alpha = 0.07f) else Color(0xFFF8F9FA),
        border = BorderStroke(1.dp, if (isSecret) MemorixPrimary.copy(alpha = 0.30f) else Color(0xFFE0E0E0)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = if (isSecret) MemorixPrimary else Color.Gray, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("비밀 $spaceLabel", fontWeight = FontWeight.Bold, color = MemorixInk, fontSize = 14.sp)
                Text("켜면 파일이 앱 내부에서 암호화되고 일반 목록에서 숨겨집니다.", color = MemorixMuted, fontSize = 12.sp)
            }
            Switch(checked = isSecret, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun EventDateRow(takenAt: Long, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("이벤트 날짜")
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = MemorixPrimary.copy(alpha = 0.05f),
            border = BorderStroke(1.2.dp, MemorixPrimary.copy(alpha = 0.30f)),
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Event, contentDescription = null, tint = MemorixPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).format(detailDateFormatter),
                    modifier = Modifier.weight(1f),
                    color = MemorixInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(Icons.Outlined.EditCalendar, contentDescription = null, tint = MemorixPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WorkLocationFields(
    countryCode: String,
    region: String,
    isLocating: Boolean,
    onCountryChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("위치")
        Text(
            text = if (isLocating) "첫 번째 사진의 위치정보를 읽는 중입니다..." else "첫 번째 사진에 위치정보가 있으면 자동 표시됩니다. 위치정보가 없으면 직접 입력해 주세요.",
            color = MemorixMuted,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = countryCode,
                onValueChange = onCountryChange,
                modifier = Modifier.weight(1f),
                label = { Text("국가") },
                placeholder = { Text("대한민국") },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = region,
                onValueChange = onRegionChange,
                modifier = Modifier.weight(1f),
                label = { Text("지역") },
                placeholder = { Text("서울") },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun OcrSection(item: MediaItemEntity, isRunning: Boolean, isPro: Boolean, onRun: () -> Unit, onCopy: () -> Unit) {
    val hasText = item.ocrText.isNotBlank()
    val runLabel = if (isRunning) "인식 중..." else if (hasText) {
        if (isPro) "재인식" else "Pro 재인식"
    } else {
        if (isPro) "텍스트 인식" else "Pro 텍스트 인식"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            SectionLabel("OCR 텍스트 인식")
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onRun, enabled = !isRunning) {
                if (isRunning) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(runLabel, fontSize = 13.sp)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (hasText) Color(0xFFF0F8FF) else Color(0xFFF8F9FA),
            border = BorderStroke(1.dp, if (hasText) Color(0xFFBBDDFF) else Color(0xFFE0E0E0)),
        ) {
            if (hasText) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("인식된 텍스트 (${item.ocrText.length}자)", color = MemorixPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "복사", tint = MemorixPrimary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp).clickable(onClick = onCopy))
                    }
                    Text(item.ocrText, color = Color(0xFF2D3748), fontSize = 13.sp, lineHeight = 20.sp)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("인식된 텍스트가 없습니다\n\"텍스트 인식\" 버튼을 눌러 실행하세요", color = Color.Gray, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(
    allTags: List<TagEntity>,
    selectedTagIds: List<Long>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onAddCustom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("태그")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allTags.forEach { tag ->
                TagChip(tag = tag, selected = tag.id in selectedTagIds, onClick = { onToggle(tag.id) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = onTagInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("태그 직접 입력", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
            )
            FilledTonalButton(onClick = onAddCustom, shape = RoundedCornerShape(12.dp)) { Text("추가", fontSize = 14.sp) }
        }
    }
}

@Composable
private fun TagChip(tag: TagEntity, selected: Boolean, onClick: () -> Unit) {
    val primary = Color(0xFF00C896)
    val unselectedText = Color(0xFF005C42)
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) primary else primary.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, primary),
    ) {
        Text(
            text = tag.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else unselectedText,
        )
    }
}

private fun detailSpaceLabel(space: MediaSpace): String = when (space) {
    MediaSpace.WORK -> "업무"
    MediaSpace.PERSONAL -> "개인"
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

private fun mergeDateKeepingTime(currentEpochMillis: Long, selectedDateMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(currentEpochMillis).atZone(zone).toLocalDateTime()
    val selectedDate = Instant.ofEpochMilli(selectedDateMillis).atZone(zone).toLocalDate()
    return selectedDate.atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024
    return if (kb >= 1024) "${String.format("%.1f", kb / 1024.0)} MB" else "$kb KB"
}

private fun createBatchZipExport(context: Context, items: List<MediaItemEntity>): File? {
    val existingItems = items.mapNotNull { item -> File(item.filePath).takeIf { it.exists() }?.let { item to it } }
    if (existingItems.isEmpty()) return null
    val outFile = File(context.cacheDir, "memorix_export_${System.currentTimeMillis()}.zip")
    ZipOutputStream(FileOutputStream(outFile)).use { zip ->
        existingItems.forEachIndexed { index, (_, file) ->
            val safeName = file.name.ifBlank { "memorix_${index + 1}" }
            zip.putNextEntry(ZipEntry("${index + 1}_${safeName}"))
            FileInputStream(file).use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return outFile
}

private fun createPhotoPdfExport(context: Context, items: List<MediaItemEntity>): File? {
    val photoFiles = items
        .filter { it.mediaType == MediaType.PHOTO }
        .mapNotNull { File(it.filePath).takeIf { file -> file.exists() } }
    if (photoFiles.isEmpty()) return null
    val outFile = File(context.cacheDir, "memorix_export_${System.currentTimeMillis()}.pdf")
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    photoFiles.forEachIndexed { index, file ->
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
        val pageWidth = 595
        val pageHeight = 842
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
        val canvas = page.canvas
        val scale = minOf(pageWidth.toFloat() / bitmap.width, (pageHeight - 56).toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (pageWidth - drawWidth) / 2f
        val top = 36f
        val dest = android.graphics.RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, dest, paint)
        paint.textSize = 12f
        paint.color = android.graphics.Color.DKGRAY
        canvas.drawText("Memorix · ${index + 1}/${photoFiles.size}", 36f, pageHeight - 24f, paint)
        document.finishPage(page)
        bitmap.recycle()
    }
    FileOutputStream(outFile).use { document.writeTo(it) }
    document.close()
    return outFile
}

private fun shareExportFile(context: Context, file: File, mimeType: String, chooserTitle: String): Boolean {
    if (!file.exists()) return false
    return try {
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, file.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
        true
    } catch (_: Exception) {
        false
    }
}

private fun shareMediaItem(context: Context, item: MediaItemEntity): Boolean {
    val file = File(item.filePath)
    if (!file.exists()) return false
    return try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mimeType = item.mimeType.ifBlank {
            when (item.mediaType) {
                MediaType.PHOTO -> "image/*"
                MediaType.VIDEO -> "video/*"
                MediaType.DOCUMENT -> "application/octet-stream"
            }
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, file.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "사진 공유"))
        true
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
