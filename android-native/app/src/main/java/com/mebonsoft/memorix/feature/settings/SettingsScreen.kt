package com.mebonsoft.memorix.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mebonsoft.memorix.BuildConfig
import com.mebonsoft.memorix.core.locale.AppLanguage
import com.mebonsoft.memorix.core.locale.MemorixStrings
import com.mebonsoft.memorix.feature.auth.AuthUiState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SettingsRowModel(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

@Composable
fun SettingsScreen(
    authState: AuthUiState,
    backupState: SettingsBackupUiState,
    settingsState: SettingsUiState,
    strings: MemorixStrings,
    onSetPin: (String, String) -> Unit,
    onClearPin: () -> Unit,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onPersonalLockEnabledChange: (Boolean) -> Unit,
    onConsumeMessages: () -> Unit,
    onExportBackup: (android.net.Uri) -> Unit,
    onRestoreBackup: (android.net.Uri) -> Unit,
    onResetAllData: () -> Unit,
    onConsumeBackupMessages: () -> Unit,
    onOpenHiddenVault: () -> Unit,
    onDeleteTag: (Long) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showTagManagementDialog by remember { mutableStateOf(false) }
    var tagPendingDelete by remember { mutableStateOf<ManagedTagRow?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let(onExportBackup)
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onRestoreBackup)
    }

    LaunchedEffect(authState.infoMessage, authState.errorMessage) {
        // 메시지는 화면에 한 번 표시한 뒤 다음 재구성에서 중복 노출되지 않도록 정리한다.
        if (authState.infoMessage != null || authState.errorMessage != null) {
            kotlinx.coroutines.delay(2_500)
            onConsumeMessages()
        }
    }

    LaunchedEffect(backupState.infoMessage, backupState.errorMessage) {
        if (backupState.infoMessage != null || backupState.errorMessage != null) {
            kotlinx.coroutines.delay(2_500)
            onConsumeBackupMessages()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(strings.settingsTitle, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = strings.settingsSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (authState.infoMessage != null || authState.errorMessage != null || backupState.infoMessage != null || backupState.errorMessage != null) {
            item {
                MessageCard(
                    message = authState.infoMessage
                        ?: authState.errorMessage
                        ?: backupState.infoMessage
                        ?: backupState.errorMessage.orEmpty(),
                    isError = authState.errorMessage != null || backupState.errorMessage != null,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.securitySection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SecuritySwitchRow(
                    icon = Icons.Outlined.Lock,
                    title = "앱 로그인 PIN",
                    subtitle = if (authState.settings.hasPin) "6자리 PIN으로 앱 실행 시 로그인" else "PIN을 설정하면 앱 실행 시 로그인 화면 표시",
                    checked = authState.settings.hasPin,
                    onCheckedChange = { checked -> if (checked) showPinDialog = true else onClearPin() },
                    actionLabel = if (authState.settings.hasPin) "PIN 변경" else "PIN 설정",
                    onAction = { showPinDialog = true },
                )
                SecuritySwitchRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "생체인증 로그인",
                    subtitle = if (authState.canUseBiometric) "지문·얼굴 또는 기기 화면잠금으로 빠르게 해제" else "기기 생체인증/화면잠금 등록 후 사용 가능",
                    checked = authState.settings.biometricEnabled,
                    enabled = authState.canUseBiometric,
                    onCheckedChange = onBiometricEnabledChange,
                )
                SecuritySwitchRow(
                    icon = Icons.Outlined.Home,
                    title = "Personal 별도 잠금",
                    subtitle = "개인 공간 접근 시 한 번 더 인증하도록 준비",
                    checked = authState.settings.personalLockEnabled,
                    enabled = authState.settings.hasPin || authState.settings.biometricEnabled,
                    onCheckedChange = onPersonalLockEnabledChange,
                )
                SettingsInfoRow(
                    item = SettingsRowModel(
                        Icons.Outlined.LockOpen,
                        "숨긴 보관함",
                        "Work·Personal에서 숨긴 항목을 인증 후 확인",
                    ),
                    onClick = onOpenHiddenVault,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.contentManagementSection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingsInfoRow(
                    item = SettingsRowModel(Icons.Outlined.Tag, strings.tagManagementTitle, strings.tagManagementSubtitle),
                    onClick = { showTagManagementDialog = true },
                )
                SettingsInfoRow(
                    item = SettingsRowModel(Icons.Outlined.Translate, strings.languageTitle, "${settingsState.selectedLanguage.nativeLabel} · ${strings.languageSubtitle}"),
                    onClick = { showLanguageDialog = true },
                )
                SettingsInfoRow(
                    item = SettingsRowModel(Icons.Outlined.Storage, strings.storageTitle, "현재 앱 관리 용량 확인"),
                    onClick = { showStorageDialog = true },
                )
                BackupRestoreSettingsSection(
                    state = backupState,
                    onBackup = { backupExportLauncher.launch(backupFileName()) },
                    onRestore = { backupRestoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                )
                SettingsDangerRow(
                    title = strings.resetAllDataTitle,
                    subtitle = "DB와 Memorix 내부 파일을 모두 삭제합니다.",
                    onClick = { showResetDialog = true },
                    enabled = !backupState.isWorking,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.appInfoSection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingsInfoRow(SettingsRowModel(Icons.Outlined.Info, strings.versionTitle, BuildConfig.VERSION_NAME))
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin, confirm ->
                onSetPin(pin, confirm)
                showPinDialog = false
            },
        )
    }

    if (showTagManagementDialog) {
        TagManagementDialog(
            tags = settingsState.managedTags,
            onDismiss = { showTagManagementDialog = false },
            onDeleteClick = { tag -> tagPendingDelete = tag },
        )
    }

    tagPendingDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            title = { Text("태그 삭제") },
            text = {
                Text(
                    TagManagementSupport.deleteWarning(
                        ManagedTag(id = tag.id, label = tag.label, usageCount = tag.usageCount),
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTag(tag.id)
                        tagPendingDelete = null
                    },
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) { Text("취소") }
            },
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            selectedLanguage = settingsState.selectedLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { language ->
                onLanguageSelected(language)
                showLanguageDialog = false
            },
        )
    }

    if (showStorageDialog) {
        SimpleInfoDialog(
            title = "저장소",
            body = "전체 ${formatBytes(backupState.managedStorageUsage.totalBytes)}\n미디어 ${formatBytes(backupState.managedStorageUsage.mediaBytes)}\nDB ${formatBytes(backupState.managedStorageUsage.databaseBytes)}\n\n초기화는 DB와 앱 내부 originals/thumbs 파일을 모두 삭제합니다.",
            onDismiss = { showStorageDialog = false },
        )
    }

    if (showResetDialog) {
        ResetAllDataDialog(
            isWorking = backupState.isWorking,
            onDismiss = { showResetDialog = false },
            onConfirm = {
                showResetDialog = false
                onResetAllData()
            },
        )
    }
}

@Composable
private fun TagManagementDialog(
    tags: List<ManagedTagRow>,
    onDismiss: () -> Unit,
    onDeleteClick: (ManagedTagRow) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("태그 관리") },
        text = {
            if (tags.isEmpty()) {
                Text(TagManagementSupport.emptyMessage)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tags, key = { it.id }) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("#${tag.label}", style = MaterialTheme.typography.titleSmall)
                                Text(tag.usageLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onDeleteClick(tag) }) {
                                Text("삭제")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}

@Composable
private fun LanguageSelectionDialog(
    selectedLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("언어") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.supported.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(language.nativeLabel, style = MaterialTheme.typography.titleSmall)
                        if (language == selectedLanguage) {
                            Text("선택됨", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SecuritySwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        if (actionLabel != null && onAction != null) {
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(item: SettingsRowModel, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsDangerRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun SimpleInfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
    )
}

@Composable
private fun ResetAllDataDialog(
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("전체 초기화", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
        text = {
            Text(
                "정말 초기화할까요?\n\n이 작업은 Memorix DB와 앱 내부에 복사된 사진·영상·문서 파일을 모두 삭제합니다. 되돌릴 수 없으니 필요하면 먼저 백업하세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isWorking,
            ) { Text(if (isWorking) "초기화 중..." else "삭제하고 초기화") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !isWorking) { Text("취소") } },
    )
}

@Composable
private fun BackupRestoreSettingsSection(
    state: SettingsBackupUiState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Outlined.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("백업 · 복구", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "전체 ${formatBytes(state.managedStorageUsage.totalBytes)} · 미디어 ${formatBytes(state.managedStorageUsage.mediaBytes)} · DB ${formatBytes(state.managedStorageUsage.databaseBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onBackup,
                enabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.isWorking) "처리 중..." else "백업")
            }
            OutlinedButton(
                onClick = onRestore,
                enabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            ) {
                Text("복구")
            }
        }
        Text(
            text = "백업 파일에는 DB와 앱 내부 originals/thumbs 파일이 함께 저장됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val valid = pin.length == 6 && confirmPin.length == 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("앱 로그인 PIN 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Memorix 실행 시 사용할 6자리 숫자 PIN을 입력하세요.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(6) },
                    label = { Text("PIN 확인") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pin, confirmPin) }, enabled = valid) {
                Text("저장")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

private fun backupFileName(): String {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "Memorix_Backup_$stamp.zip"
}
