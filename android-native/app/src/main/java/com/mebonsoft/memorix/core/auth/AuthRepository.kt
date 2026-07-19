package com.mebonsoft.memorix.core.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "memorix_auth")

data class AuthSettings(
    val hasPin: Boolean = false,
    val biometricEnabled: Boolean = false,
    val personalLockEnabled: Boolean = false,
)

enum class AuthGateState { Checking, Locked, Unlocked }

object AuthPolicy {
    fun shouldLock(
        hasPin: Boolean,
        biometricEnabled: Boolean,
        canUseBiometric: Boolean,
    ): Boolean = hasPin || (biometricEnabled && canUseBiometric)

    fun isValidPin(pin: String): Boolean = pin.length == 6 && pin.all { it.isDigit() }
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val PinHash = stringPreferencesKey("memorix_pin_hash")
        val BiometricEnabled = booleanPreferencesKey("memorix_biometric_enabled")
        val PersonalLockEnabled = booleanPreferencesKey("memorix_personal_lock_enabled")
    }

    val settings: Flow<AuthSettings> = context.authDataStore.data.map { preferences ->
        AuthSettings(
            hasPin = !preferences[Keys.PinHash].isNullOrBlank(),
            biometricEnabled = preferences[Keys.BiometricEnabled] == true,
            personalLockEnabled = preferences[Keys.PersonalLockEnabled] == true,
        )
    }

    suspend fun setPin(pin: String) {
        require(AuthPolicy.isValidPin(pin)) { "PIN은 6자리 숫자여야 합니다." }
        context.authDataStore.edit { preferences ->
            preferences[Keys.PinHash] = hashPin(pin)
        }
    }

    suspend fun clearPin() {
        context.authDataStore.edit { preferences ->
            preferences.remove(Keys.PinHash)
            preferences[Keys.BiometricEnabled] = false
            preferences[Keys.PersonalLockEnabled] = false
        }
    }

    suspend fun verifyPin(input: String): Boolean {
        val storedHash = context.authDataStore.data.map { it[Keys.PinHash] }.first()
        return storedHash != null && storedHash == hashPin(input)
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.authDataStore.edit { preferences ->
            preferences[Keys.BiometricEnabled] = enabled
        }
    }

    suspend fun setPersonalLockEnabled(enabled: Boolean) {
        context.authDataStore.edit { preferences ->
            preferences[Keys.PersonalLockEnabled] = enabled
        }
    }

    fun canUseBiometric(): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun hashPin(pin: String): String {
        val salted = "memorix-local-pin:$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray())
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
