package com.mebonsoft.memorix.feature.work.compose

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaComposeScreen(
    initialUris: List<Uri> = emptyList(),
    space: MediaSpace = MediaSpace.WORK,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    viewModel: MediaComposeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = composeSpaceProfile(space)
    val context = LocalContext.current
    var showDiscardDialog by remember { mutableStateOf(false) }
    val canSave = uiState.mediaUris.isNotEmpty() && !uiState.isSaving

    val addDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addMediaUris(uris) }

    LaunchedEffect(space) {
        viewModel.setSpace(space)
    }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty() && uiState.mediaUris.isEmpty()) {
            viewModel.setMediaUris(initialUris)
        }
    }

    LaunchedEffect(uiState.saveComplete) {
        if (uiState.saveComplete) onSaveComplete()
    }

    LaunchedEffect(uiState.mediaUris) {
        if (uiState.mediaUris.isNotEmpty() && uiState.countryCode.isBlank() && uiState.region.isBlank()) {
            val location = readFirstPhotoLocation(context, uiState.mediaUris)
            if (location == null) {
                viewModel.showLocationHint("첫 번째 사진에 위치정보가 없습니다. 위치를 남기려면 직접 입력해 주세요.")
            } else {
                viewModel.applyAutoLocation(location.countryCode, location.region)
            }
        }
    }

    BackHandler {
        if (uiState.hasContent) showDiscardDialog = true else onBack()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("작성 내용을 저장할까요?") },
            text = { Text("뒤로 가기 전에 지금 입력한 내용을 저장할 수 있습니다.") },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        showDiscardDialog = false
                        viewModel.save()
                    },
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                        Text("저장 안 함")
                    }
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("계속 작성")
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.title, fontWeight = FontWeight.Bold, color = MemorixInk) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.hasContent) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::save,
                        enabled = canSave,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MemorixPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = "저장", tint = MemorixPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MediaThumbnailRow(
                uris = uiState.mediaUris,
                onRemove = viewModel::removeMediaAt,
                onAdd = {
                    addDocumentLauncher.launch("*/*")
                },
            )

            EventDateCard(
                eventDateMillis = uiState.eventDateMillis,
                hint = uiState.eventDateHint,
            )

            uiState.errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionLabel(profile.noteLabel)
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::updateNote,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text(profile.notePlaceholder, color = MemorixMuted) },
                shape = RoundedCornerShape(14.dp),
            )

            SectionLabel(profile.tagLabel)
            TagInputRow(
                value = uiState.newTagText,
                onValueChange = viewModel::updateNewTagText,
                onAdd = viewModel::addCustomTag,
                enabled = !uiState.isSaving,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.availableTags.forEach { tag ->
                    val selected = tag.id in uiState.selectedTagIds
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleTag(tag.id) },
                        label = {
                            Text(
                                text = tag.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MemorixPrimary.copy(alpha = 0.16f),
                            selectedLabelColor = MemorixPrimary,
                        ),
                    )
                }
            }

            SectionLabel(profile.locationLabel)
            Text(
                text = if (uiState.locationHint.isBlank()) profile.locationHint else uiState.locationHint,
                style = MaterialTheme.typography.bodySmall,
                color = MemorixMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.countryCode,
                    onValueChange = viewModel::updateCountryCode,
                    modifier = Modifier.weight(1f),
                    label = { Text("국가") },
                    leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.region,
                    onValueChange = viewModel::updateRegion,
                    modifier = Modifier.weight(1f),
                    label = { Text("지역") },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun EventDateCard(
    eventDateMillis: Long?,
    hint: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MemorixPrimary.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Event,
            contentDescription = null,
            tint = MemorixPrimary,
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "이벤트 날짜",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MemorixInk,
            )
            Text(
                text = eventDateMillis?.let { formatComposeEventDate(it) } ?: "자동 감지 대기 중",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MemorixInk,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MemorixMuted,
            )
        }
    }
}

@Composable
private fun TagInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("태그 직접 입력", fontSize = 14.sp) },
            prefix = { Text("#", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
        )
        Button(
            onClick = onAdd,
            enabled = enabled && value.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MemorixPrimary,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
        ) {
            Text(
                text = "추가",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun MediaThumbnailRow(
    uris: List<Uri>,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        buildComposeMediaPreviewRows(uris.size).forEach { row ->
            val uri = uris[row.sourceIndex]
            val context = LocalContext.current
            val mimeType = remember(uri) { context.contentResolver.getType(uri).orEmpty() }
            val isDocument = remember(mimeType) {
                mimeType.isNotBlank() && !mimeType.startsWith("image/") && !mimeType.startsWith("video/")
            }
            val isVideo = remember(mimeType) {
                mimeType.startsWith("video/")
            }
            val displayName = remember(uri) {
                val type = context.contentResolver.getType(uri).orEmpty()
                uri.lastPathSegment?.substringAfterLast('/') ?: type.ifBlank { "선택한 미디어" }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isDocument) 120.dp else 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDocument) Color(0xFFF5F5F5) else Color(0xFFECEFF3))
                    .border(1.dp, if (isDocument) Color(0xFFE0E0E0) else Color.Transparent, RoundedCornerShape(12.dp)),
            ) {
                if (isDocument) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(displayName, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .allowHardware(false)
                            .build(),
                        contentDescription = "선택한 사진 ${row.orderLabel}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Icon(
                        Icons.Outlined.Fullscreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(8.dp))
                            .padding(6.dp),
                    )
                    if (isVideo) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.76f),
                            modifier = Modifier.align(Alignment.Center).size(52.dp),
                        )
                    }
                }
                Text(
                    text = row.orderLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = { onRemove(row.sourceIndex) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(999.dp)),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "선택한 미디어 제거",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        AddMediaButton(onClick = onAdd)
    }
}

@Composable
private fun AddMediaButton(onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD6DAE0)),
    ) {
        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text("미디어 추가")
    }
}

private data class ComposeLocationDraft(
    val countryCode: String,
    val region: String,
)

private val composeEventDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

private fun formatComposeEventDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(composeEventDateFormatter)

private suspend fun readFirstPhotoLocation(context: Context, uris: List<Uri>): ComposeLocationDraft? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    uris.firstNotNullOfOrNull { uri ->
        val mimeType = resolver.getType(uri).orEmpty()
        if (mimeType.isNotBlank() && !mimeType.startsWith("image/")) return@firstNotNullOfOrNull null
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val latLong = ExifInterface(input).latLong ?: return@use null
                val placemark = Geocoder(context, Locale.KOREAN)
                    .getFromLocation(latLong[0].toDouble(), latLong[1].toDouble(), 1)
                    ?.firstOrNull()
                ComposeLocationDraft(
                    countryCode = placemark?.countryName ?: placemark?.countryCode ?: "",
                    region = placemark?.adminArea ?: placemark?.locality ?: "",
                ).takeIf { it.countryCode.isNotBlank() || it.region.isNotBlank() }
            }
        }.getOrNull()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MemorixInk,
    )
}
