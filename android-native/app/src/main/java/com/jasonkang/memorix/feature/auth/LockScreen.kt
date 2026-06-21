package com.jasonkang.memorix.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jasonkang.memorix.core.auth.AuthGateState
import com.jasonkang.memorix.core.designsystem.theme.MemorixPrimary
import com.jasonkang.memorix.core.designsystem.theme.MemorixSecondary

@Composable
fun LockScreen(
    state: AuthUiState,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometricSuccess: () -> Unit,
    onBiometricError: (String) -> Unit,
) {
    val launchBiometric = rememberBiometricLoginLauncher(
        onSuccess = onBiometricSuccess,
        onError = onBiometricError,
    )
    val biometricReady = state.canUseBiometric && (state.settings.biometricEnabled || !state.settings.hasPin)

    LaunchedEffect(state.gateState, biometricReady) {
        if (state.gateState == AuthGateState.Locked && biometricReady) {
            launchBiometric()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(MemorixPrimary, MemorixSecondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Memorix", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.settings.hasPin) "PIN을 입력하세요" else "생체인증으로 로그인하세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(34.dp))

        if (state.settings.hasPin) {
            PinDots(length = state.pinInput.length)
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.weight(1f))
        if (state.settings.hasPin) {
            NumericKeypad(onDigit = onDigit, onDelete = onDelete)
        }
        if (biometricReady) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.clickable(onClick = launchBiometric),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MemorixPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = MemorixPrimary, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("생체인증", style = MaterialTheme.typography.labelMedium, color = MemorixPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PinDots(length: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) { index ->
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (index < length) MemorixPrimary else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "delete"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (key.isBlank()) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = key.isNotBlank()) {
                                if (key == "delete") onDelete() else onDigit(key)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "delete") {
                            Icon(Icons.Outlined.Backspace, contentDescription = "삭제")
                        } else if (key.isNotBlank()) {
                            Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
