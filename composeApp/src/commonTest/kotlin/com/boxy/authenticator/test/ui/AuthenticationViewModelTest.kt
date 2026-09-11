package com.boxy.authenticator.test.ui

import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.crypto.PasscodeManager
import com.boxy.authenticator.test.InMemoryPreferenceStore
import com.boxy.authenticator.ui.viewmodels.AuthenticationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    @Test
    fun `correct passcode unlocks and clears sensitive input`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val manager = testPasscodeManager()
            val settings = SettingsDataStore(InMemoryPreferenceStore())
            settings.setAppLockEnabled(true, manager.create("correct"))
            val viewModel = AuthenticationViewModel(settings, manager)
            var result: Boolean? = null

            viewModel.updatePassword("correct")
            viewModel.verifyPassword { result = it }
            advanceUntilIdle()

            assertEquals(true, result)
            assertEquals("", viewModel.uiState.value.password)
            assertNull(viewModel.uiState.value.passwordError)
            assertFalse(viewModel.uiState.value.isVerifyingPassword)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `wrong and malformed credentials fail closed`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val manager = testPasscodeManager()
            val settings = SettingsDataStore(InMemoryPreferenceStore())
            settings.setAppLockEnabled(true, "malformed")
            val viewModel = AuthenticationViewModel(settings, manager)
            var result: Boolean? = null

            viewModel.updatePassword("anything")
            viewModel.verifyPassword { result = it }
            advanceUntilIdle()

            assertEquals(false, result)
            assertEquals("", viewModel.uiState.value.password)
            assertTrue(viewModel.uiState.value.passwordError?.isNotBlank() == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful legacy unlock upgrades stored credential`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val manager = testPasscodeManager()
            val settings = SettingsDataStore(InMemoryPreferenceStore())
            val legacy = derive("legacy".encodeToByteArray(), ByteArray(32), 32).decodeToString()
            settings.setAppLockEnabled(true, legacy)
            val viewModel = AuthenticationViewModel(settings, manager)

            viewModel.updatePassword("legacy")
            viewModel.verifyPassword { assertTrue(it) }
            advanceUntilIdle()

            val upgraded = settings.getPasscodeHash()
            assertNotEquals(legacy, upgraded)
            assertTrue(upgraded?.startsWith("v2:") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `repeated submit cannot start concurrent verification`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val gate = CompletableDeferred<Unit>()
            var shouldBlock = false
            var derivations = 0
            val manager = PasscodeManager(
                deriveKey = { password, salt, size ->
                    derivations++
                    if (shouldBlock) gate.await()
                    derive(password, salt, size)
                },
                generateSalt = { size -> ByteArray(size) { 3 } },
            )
            val settings = SettingsDataStore(InMemoryPreferenceStore())
            settings.setAppLockEnabled(true, manager.create("correct"))
            derivations = 0
            shouldBlock = true
            val viewModel = AuthenticationViewModel(settings, manager)
            var callbacks = 0
            viewModel.updatePassword("correct")

            viewModel.verifyPassword { callbacks++ }
            viewModel.verifyPassword { callbacks++ }

            assertTrue(viewModel.uiState.value.isVerifyingPassword)
            assertEquals(1, derivations)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, callbacks)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `biometric unlock state is gated by its persisted setting`() = runTest {
        val manager = testPasscodeManager()
        val settings = SettingsDataStore(InMemoryPreferenceStore())
        settings.setAppLockEnabled(true, manager.create("correct"))
        val viewModel = AuthenticationViewModel(settings, manager)

        assertFalse(viewModel.isBiometricUnlockEnabled)
        settings.setBiometricUnlockEnabled(true)
        assertTrue(viewModel.isBiometricUnlockEnabled)
        settings.setBiometricUnlockEnabled(false)
        assertFalse(viewModel.isBiometricUnlockEnabled)
    }

    @Test
    fun `legacy migration write failure does not reject a valid unlock`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val manager = PasscodeManager(
                deriveKey = ::derive,
                generateSalt = { throw IllegalStateException("random source unavailable") },
            )
            val settings = SettingsDataStore(InMemoryPreferenceStore())
            val legacy = derive("legacy".encodeToByteArray(), ByteArray(32), 32).decodeToString()
            settings.setAppLockEnabled(true, legacy)
            val viewModel = AuthenticationViewModel(settings, manager)
            var result: Boolean? = null

            viewModel.updatePassword("legacy")
            viewModel.verifyPassword { result = it }
            advanceUntilIdle()

            assertEquals(true, result)
            assertEquals(legacy, settings.getPasscodeHash())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun testPasscodeManager() = PasscodeManager(
        deriveKey = ::derive,
        generateSalt = { size -> ByteArray(size) { 3 } },
    )

    private fun derive(password: ByteArray, salt: ByteArray, size: Int): ByteArray =
        ByteArray(size) { index ->
            val passwordByte = password[index % password.size].toInt() and 0xff
            val saltByte = salt[index % salt.size].toInt() and 0xff
            ('A'.code + ((passwordByte + saltByte + index) % 26)).toByte()
        }
}
