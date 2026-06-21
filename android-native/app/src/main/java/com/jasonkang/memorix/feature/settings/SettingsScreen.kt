package com.jasonkang.memorix.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
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
import com.jasonkang.memorix.feature.auth.AuthUiState

data class SettingsRowModel(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

@Composable
fun SettingsScreen(
    authState: AuthUiState,
    onSetPin: (String, String) -> Unit,
    onClearPin: () -> Unit,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onPersonalLockEnabledChange: (Boolean) -> Unit,
    onConsumeMessages: () -> Unit,
) {
    var showPinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(authState.infoMessage, authState.errorMessage) {
        // 메시지는 화면에 한 번 표시한 뒤 다음 재구성에서 중복 노출되지 않도록 정리한다.
        if (authState.infoMessage != null || authState.errorMessage != null) {
            kotlinx.coroutines.delay(2_500)
            onConsumeMessages()
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
                Text("설정", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "로그인, 앱 잠금, 생체인증을 기기 안에서 안전하게 관리합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (authState.infoMessage != null || authState.errorMessage != null) {
            item {
                MessageCard(
                    message = authState.infoMessage ?: authState.errorMessage.orEmpty(),
                    isError = authState.errorMessage != null,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("로그인·보안", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("콘텐츠 관리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingsInfoRow(SettingsRowModel(Icons.Outlined.Tag, "태그 관리", "Work·Personal 태그 관리 화면 연결 예정"))
                SettingsInfoRow(SettingsRowModel(Icons.Outlined.Storage, "저장소", "용량, 저장 경로, 정리 도구"))
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("앱 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingsInfoRow(SettingsRowModel(Icons.Outlined.Info, "Memorix", "로컬 중심 사진·영상·문서 보관함"))
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
private fun SettingsInfoRow(item: SettingsRowModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
