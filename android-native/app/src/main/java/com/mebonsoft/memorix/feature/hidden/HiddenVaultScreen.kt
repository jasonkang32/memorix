package com.mebonsoft.memorix.feature.hidden

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.designsystem.theme.MemorixInk
import com.mebonsoft.memorix.core.designsystem.theme.MemorixMuted
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPersonalEnd
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPersonalStart
import com.mebonsoft.memorix.core.designsystem.theme.MemorixPrimary
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkDeep
import com.mebonsoft.memorix.core.designsystem.theme.MemorixWorkSoft
import com.mebonsoft.memorix.feature.auth.rememberBiometricLoginLauncher
import com.mebonsoft.memorix.feature.work.timeline.WorkTimeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenVaultScreen(
    onBack: () -> Unit,
    onMediaClick: (Long) -> Unit,
    viewModel: HiddenVaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    val launchBiometric = rememberBiometricLoginLauncher(
        onSuccess = {
            viewModel.unlockByBiometric()
            pin = ""
        },
        onError = { /* 기기 인증 UI 자체 오류는 PIN 입력 영역 메시지와 별도로 조용히 둔다. */ },
    )

    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        if (uiState.infoMessage != null || uiState.errorMessage != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("숨긴 보관함", fontWeight = FontWeight.Bold, color = MemorixInk) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!uiState.isUnlocked) {
                UnlockVaultBlock(
                    pin = pin,
                    hasAuthCredential = uiState.hasAuthCredential,
                    canUseBiometric = uiState.canUseBiometric,
                    errorMessage = uiState.errorMessage,
                    onPinChange = { input -> pin = input.filter { it.isDigit() }.take(6) },
                    onUnlockPin = { viewModel.unlockByPin(pin) },
                    onUnlockBiometric = launchBiometric,
                )
            } else {
                SpaceFilterRow(
                    selectedSpace = uiState.selectedSpace,
                    workCount = uiState.workCount,
                    personalCount = uiState.personalCount,
                    onSelect = viewModel::selectSpace,
                )

                uiState.infoMessage?.let { message ->
                    VaultMessage(message = message, isError = false)
                }
                uiState.errorMessage?.let { message ->
                    VaultMessage(message = message, isError = true)
                }

                Text(
                    text = "자물쇠를 누르면 일반 ${if (uiState.selectedSpace == MediaSpace.WORK) "Work" else "Personal"} 목록으로 복원됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MemorixMuted,
                )

                if (uiState.items.isEmpty()) {
                    EmptyVaultBlock(space = uiState.selectedSpace)
                } else {
                    WorkTimeline(
                        items = uiState.items,
                        tagsByMediaId = uiState.tagsByMediaId,
                        modifier = Modifier.weight(1f),
                        onItemClick = { item -> onMediaClick(item.id) },
                        onSecretClick = viewModel::restoreItems,
                        secretActionContentDescription = "일반 목록으로 복원",
                        secretActionUnlocked = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnlockVaultBlock(
    pin: String,
    hasAuthCredential: Boolean,
    canUseBiometric: Boolean,
    errorMessage: String?,
    onPinChange: (String) -> Unit,
    onUnlockPin: () -> Unit,
    onUnlockBiometric: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MemorixPrimary,
            modifier = Modifier.size(44.dp),
        )
        Text("인증 후 숨긴 항목을 확인합니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = if (hasAuthCredential) {
                "PIN 또는 생체인증으로 Work/Personal에서 숨긴 항목을 열 수 있습니다."
            } else {
                "먼저 설정에서 앱 로그인 PIN 또는 생체인증을 설정해 주세요."
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
            Button(
                onClick = onUnlockPin,
                enabled = pin.length == 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MemorixPrimary, contentColor = Color.White),
            ) { Text("PIN으로 열기") }
            if (canUseBiometric) {
                TextButton(onClick = onUnlockBiometric) {
                    Icon(Icons.Outlined.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("생체인증으로 열기")
                }
            }
        }
    }
}

@Composable
private fun SpaceFilterRow(
    selectedSpace: MediaSpace,
    workCount: Int,
    personalCount: Int,
    onSelect: (MediaSpace) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onSelect(MediaSpace.WORK) },
            label = { Text("Work $workCount") },
            leadingIcon = { Icon(Icons.Outlined.Work, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = if (selectedSpace == MediaSpace.WORK) {
                AssistChipDefaults.assistChipColors(containerColor = MemorixWorkSoft, labelColor = MemorixWorkDeep)
            } else {
                AssistChipDefaults.assistChipColors()
            },
        )
        AssistChip(
            onClick = { onSelect(MediaSpace.PERSONAL) },
            label = { Text("Personal $personalCount") },
            leadingIcon = { Icon(Icons.Outlined.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = if (selectedSpace == MediaSpace.PERSONAL) {
                AssistChipDefaults.assistChipColors(containerColor = MemorixPersonalStart.copy(alpha = 0.14f), labelColor = MemorixPersonalEnd)
            } else {
                AssistChipDefaults.assistChipColors()
            },
        )
    }
}

@Composable
private fun VaultMessage(message: String, isError: Boolean) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MemorixPrimary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MemorixPrimary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyVaultBlock(space: MediaSpace) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = MemorixMuted, modifier = Modifier.size(36.dp))
            Text(
                text = "숨긴 ${if (space == MediaSpace.WORK) "Work" else "Personal"} 항목이 없습니다",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "일반 목록에서 카드의 자물쇠를 누르면 이곳으로 이동합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MemorixMuted,
            )
        }
    }
}
