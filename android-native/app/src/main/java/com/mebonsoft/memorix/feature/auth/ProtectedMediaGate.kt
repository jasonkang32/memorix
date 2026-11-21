package com.mebonsoft.memorix.feature.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.auth.AuthRepository
import com.mebonsoft.memorix.core.security.ProtectedMediaSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 잠금·비밀 항목을 열 때의 인증 방법. 생체정보가 등록되어 있으면 생체인증을 우선한다. */
enum class ProtectedUnlockMethod { Biometric, Pin, None }

data class ProtectedMediaGateUiState(
    val unlocked: Boolean = false,
    val hasPin: Boolean = false,
    val hasBiometric: Boolean = false,
    val errorMessage: String? = null,
) {
    val preferredMethod: ProtectedUnlockMethod
        get() = when {
            hasBiometric -> ProtectedUnlockMethod.Biometric
            hasPin -> ProtectedUnlockMethod.Pin
            else -> ProtectedUnlockMethod.None
        }
}

@HiltViewModel
class ProtectedMediaGateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val session: ProtectedMediaSession,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProtectedMediaGateUiState> = combine(
        session.unlocked,
        authRepository.settings,
        errorMessage,
    ) { unlocked, settings, error ->
        ProtectedMediaGateUiState(
            unlocked = unlocked,
            hasPin = settings.hasPin,
            hasBiometric = authRepository.hasEnrolledBiometric(),
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProtectedMediaGateUiState(unlocked = session.unlocked.value))

    fun unlockByBiometric() {
        errorMessage.value = null
        session.unlock()
    }

    /** PIN도 생체정보도 없으면 확인할 수단이 없으므로 그대로 연다. */
    fun unlockWithoutCredential() {
        session.unlock()
    }

    fun unlockByPin(pin: String) {
        viewModelScope.launch {
            if (authRepository.verifyPin(pin)) {
                errorMessage.value = null
                session.unlock()
            } else {
                errorMessage.update { "PIN이 올바르지 않습니다." }
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}

/**
 * 보호 항목이면 인증 전까지 내용을 가리는 화면을 보여주고, 인증되면 [content]를 그린다.
 * 화면에 들어오면 바로 생체인증(등록된 경우) 또는 PIN 입력을 띄운다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedMediaGate(
    isProtected: Boolean,
    onBack: () -> Unit,
    viewModel: ProtectedMediaGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!isProtected || state.unlocked) {
        content()
        return
    }

    var showPinDialog by rememberSaveable { mutableStateOf(false) }
    var pin by rememberSaveable { mutableStateOf("") }
    val launchBiometric = rememberProtectedBiometricPrompt(
        allowPinFallback = state.hasPin,
        onSuccess = viewModel::unlockByBiometric,
        onUsePin = { if (state.hasPin) showPinDialog = true },
    )
    val requestUnlock: () -> Unit = {
        when (state.preferredMethod) {
            ProtectedUnlockMethod.Biometric -> launchBiometric()
            ProtectedUnlockMethod.Pin -> showPinDialog = true
            ProtectedUnlockMethod.None -> viewModel.unlockWithoutCredential()
        }
    }
    LaunchedEffect(Unit) { requestUnlock() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("잠긴 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(
                "인증해야 볼 수 있는 기록입니다.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = requestUnlock, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.preferredMethod == ProtectedUnlockMethod.Biometric) "생체인증으로 열기" else "PIN으로 열기")
            }
            if (state.preferredMethod == ProtectedUnlockMethod.Biometric && state.hasPin) {
                OutlinedButton(onClick = { showPinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("PIN으로 열기")
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pin = ""
                viewModel.clearError()
            },
            title = { Text("PIN 입력") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input -> pin = input.filter { it.isDigit() }.take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIN 6자리") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    )
                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.unlockByPin(pin) }, enabled = pin.length == 6) { Text("열기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pin = ""
                    viewModel.clearError()
                }) { Text("취소") }
            },
        )
    }
}

/** 생체인증 전용 프롬프트. 아래 버튼은 PIN이 있으면 "PIN 입력"으로 PIN 대화상자를 연다. */
@Composable
private fun rememberProtectedBiometricPrompt(
    allowPinFallback: Boolean,
    onSuccess: () -> Unit,
    onUsePin: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    return remember(context, allowPinFallback) {
        launch@{
            val activity = context.findHostActivity() ?: run {
                onUsePin()
                return@launch
            }
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // 사용자가 PIN 입력을 고르거나 생체인증이 막힌 경우(시도 초과 등) PIN으로 넘어간다.
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) {
                            onUsePin()
                        }
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("잠긴 기록 열기")
                .setSubtitle("지문 또는 얼굴로 인증하세요")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(if (allowPinFallback) "PIN 입력" else "취소")
                .build()
            prompt.authenticate(info)
        }
    }
}

private fun Context.findHostActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
