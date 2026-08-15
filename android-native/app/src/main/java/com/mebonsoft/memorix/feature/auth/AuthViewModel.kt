package com.mebonsoft.memorix.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.auth.AuthGateState
import com.mebonsoft.memorix.core.auth.AuthPolicy
import com.mebonsoft.memorix.core.auth.AuthRepository
import com.mebonsoft.memorix.core.auth.AuthSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val gateState: AuthGateState = AuthGateState.Checking,
    val settings: AuthSettings = AuthSettings(),
    val canUseBiometric: Boolean = false,
    val pinInput: String = "",
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        AuthUiState(canUseBiometric = authRepository.canUseBiometric()),
    )

    val uiState: StateFlow<AuthUiState> = combine(
        mutableState,
        authRepository.settings,
    ) { state, settings ->
        val canUseBiometric = authRepository.canUseBiometric()
        val gateState = when (state.gateState) {
            AuthGateState.Checking -> {
                if (AuthPolicy.shouldLock(settings.hasPin, settings.biometricEnabled, canUseBiometric)) {
                    AuthGateState.Locked
                } else {
                    AuthGateState.Unlocked
                }
            }
            else -> state.gateState
        }
        state.copy(
            gateState = gateState,
            settings = settings,
            canUseBiometric = canUseBiometric,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = mutableState.value,
    )

    fun appendPinDigit(digit: String) {
        val current = mutableState.value.pinInput
        if (current.length >= 6 || digit.length != 1 || !digit.first().isDigit()) return
        val next = current + digit
        mutableState.value = mutableState.value.copy(pinInput = next, errorMessage = null, infoMessage = null)
        if (next.length == 6) verifyPin(next)
    }

    fun deletePinDigit() {
        val current = mutableState.value.pinInput
        if (current.isEmpty()) return
        mutableState.value = mutableState.value.copy(pinInput = current.dropLast(1), errorMessage = null)
    }

    fun verifyPin(pin: String = mutableState.value.pinInput) {
        viewModelScope.launch {
            val ok = authRepository.verifyPin(pin)
            mutableState.value = if (ok) {
                mutableState.value.copy(
                    gateState = AuthGateState.Unlocked,
                    pinInput = "",
                    errorMessage = null,
                    infoMessage = null,
                )
            } else {
                mutableState.value.copy(
                    pinInput = "",
                    errorMessage = "PIN이 올바르지 않습니다.",
                )
            }
        }
    }

    fun unlockByBiometric() {
        mutableState.value = mutableState.value.copy(
            gateState = AuthGateState.Unlocked,
            pinInput = "",
            errorMessage = null,
            infoMessage = null,
        )
    }

    fun setPin(pin: String, confirmPin: String) {
        viewModelScope.launch {
            when {
                !AuthPolicy.isValidPin(pin) -> {
                    mutableState.value = mutableState.value.copy(errorMessage = "PIN은 6자리 숫자로 입력하세요.")
                }
                pin != confirmPin -> {
                    mutableState.value = mutableState.value.copy(errorMessage = "확인 PIN이 일치하지 않습니다.")
                }
                else -> {
                    authRepository.setPin(pin)
                    mutableState.value = mutableState.value.copy(
                        gateState = AuthGateState.Unlocked,
                        errorMessage = null,
                        infoMessage = "앱 잠금 PIN이 설정되었습니다.",
                    )
                }
            }
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            authRepository.clearPin()
            mutableState.value = mutableState.value.copy(
                gateState = AuthGateState.Unlocked,
                pinInput = "",
                errorMessage = null,
                infoMessage = "앱 잠금이 해제되었습니다.",
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !authRepository.canUseBiometric()) {
                mutableState.value = mutableState.value.copy(errorMessage = "이 기기에서 사용할 수 있는 생체인증/화면잠금이 없습니다.")
                return@launch
            }
            authRepository.setBiometricEnabled(enabled)
            mutableState.value = mutableState.value.copy(
                errorMessage = null,
                infoMessage = if (enabled) "생체인증 로그인이 켜졌습니다." else "생체인증 로그인이 꺼졌습니다.",
            )
        }
    }

    fun setPersonalLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = uiState.value.settings
            if (enabled && !currentSettings.hasPin && !currentSettings.biometricEnabled) {
                mutableState.value = mutableState.value.copy(errorMessage = "먼저 앱 잠금 PIN 또는 생체인증을 설정하세요.")
                return@launch
            }
            authRepository.setPersonalLockEnabled(enabled)
            mutableState.value = mutableState.value.copy(
                errorMessage = null,
                infoMessage = if (enabled) "개인 별도 잠금이 켜졌습니다." else "개인 별도 잠금이 꺼졌습니다.",
            )
        }
    }

    fun consumeMessages() {
        mutableState.value = mutableState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun showError(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message, infoMessage = null)
    }
}
