package com.jasonkang.memorix.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPolicyTest {
    @Test
    fun shouldLock_whenPinExists() {
        assertTrue(AuthPolicy.shouldLock(hasPin = true, biometricEnabled = false, canUseBiometric = false))
    }

    @Test
    fun shouldLock_whenBiometricEnabledAndAvailable() {
        assertTrue(AuthPolicy.shouldLock(hasPin = false, biometricEnabled = true, canUseBiometric = true))
    }

    @Test
    fun shouldNotLock_whenNoPinAndBiometricUnavailable() {
        assertFalse(AuthPolicy.shouldLock(hasPin = false, biometricEnabled = true, canUseBiometric = false))
        assertFalse(AuthPolicy.shouldLock(hasPin = false, biometricEnabled = false, canUseBiometric = true))
    }

    @Test
    fun pinMustBeSixDigits() {
        assertTrue(AuthPolicy.isValidPin("123456"))
        assertFalse(AuthPolicy.isValidPin("12345"))
        assertFalse(AuthPolicy.isValidPin("1234567"))
        assertFalse(AuthPolicy.isValidPin("12a456"))
    }
}
