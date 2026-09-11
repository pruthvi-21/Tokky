package com.boxy.authenticator.test.ui

import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.crypto.PasscodeManager
import com.boxy.authenticator.domain.models.enums.AppTheme
import com.boxy.authenticator.domain.models.enums.TokenTapResponse
import com.boxy.authenticator.domain.models.enums.LabelVisibility
import com.boxy.authenticator.domain.models.form.SettingChangeEvent
import com.boxy.authenticator.test.InMemoryPreferenceStore
import com.boxy.authenticator.ui.viewmodels.SettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun `initial state reflects persisted settings`() {
        val store = InMemoryPreferenceStore()
        val settings = SettingsDataStore(store).apply {
            setAppTheme(AppTheme.DARK)
            setTokenTapResponse(TokenTapResponse.LONG_PRESS)
            setLabelVisibility(LabelVisibility.NEVER)
            setBlockScreenshotsEnabled(false)
        }

        val state = SettingsViewModel(settings, testPasscodeManager()).uiState.value.settings

        assertEquals(AppTheme.DARK, state.appTheme)
        assertEquals(TokenTapResponse.LONG_PRESS, state.tokenTapResponse)
        assertEquals(LabelVisibility.NEVER, state.labelVisibility)
        assertFalse(state.isBlockScreenshotsEnabled)
    }

    @Test
    fun `synchronous setting events update state and persistence`() {
        val store = InMemoryPreferenceStore()
        val settings = SettingsDataStore(store)
        settings.setAppLockEnabled(true, "credential")
        val viewModel = SettingsViewModel(settings, testPasscodeManager())

        viewModel.onEvent(SettingChangeEvent.AppThemeChanged(AppTheme.LIGHT))
        viewModel.onEvent(SettingChangeEvent.TokenTapResponseChanged(TokenTapResponse.SINGLE_TAP))
        viewModel.onEvent(SettingChangeEvent.LabelVisibilityChanged(LabelVisibility.WHEN_EXPANDED))
        viewModel.onEvent(SettingChangeEvent.ShowLabelCountsChanged(true))
        viewModel.onEvent(SettingChangeEvent.LockScreenPinPadChanged(true))
        viewModel.onEvent(SettingChangeEvent.BackupAlertsChanged(true))
        viewModel.onEvent(SettingChangeEvent.BiometricUnlockChanged(true))
        viewModel.onEvent(SettingChangeEvent.BlockScreenshotsChanged(false))
        viewModel.onEvent(SettingChangeEvent.LockSensitiveFieldsChanged(false))

        assertEquals(viewModel.uiState.value.settings, settings.getSettings())
        assertEquals(AppTheme.LIGHT, settings.getAppTheme())
        assertEquals(TokenTapResponse.SINGLE_TAP, settings.getTokenTapResponse())
        assertEquals(LabelVisibility.WHEN_EXPANDED, settings.getLabelVisibility())
        assertTrue(settings.isShowLabelCountsEnabled())
        assertTrue(settings.isLockscreenPinPadEnabled())
        assertTrue(settings.isDisableBackupAlertsEnabled())
        assertTrue(settings.isBiometricUnlockEnabled())
        assertFalse(settings.isBlockScreenshotsEnabled())
        assertFalse(settings.isLockSensitiveFieldsEnabled())
    }

    @Test
    fun `dialog visibility changes independently`() {
        val viewModel = SettingsViewModel(
            SettingsDataStore(InMemoryPreferenceStore()),
            testPasscodeManager(),
        )

        viewModel.showEnableAppLockDialog(true)
        assertTrue(viewModel.uiState.value.showEnableAppLockDialog)
        assertFalse(viewModel.uiState.value.showDisableAppLockDialog)

        viewModel.showDisableAppLockDialog(true)
        viewModel.showEnableAppLockDialog(false)
        assertFalse(viewModel.uiState.value.showEnableAppLockDialog)
        assertTrue(viewModel.uiState.value.showDisableAppLockDialog)
    }

    @Test
    fun `app lock enable and disable preserve security invariants`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val settings = SettingsDataStore(InMemoryPreferenceStore())
            val viewModel = SettingsViewModel(settings, testPasscodeManager())

            viewModel.onEvent(SettingChangeEvent.AppLockChanged(true, "secret"))
            advanceUntilIdle()
            assertTrue(settings.isAppLockEnabled())
            assertTrue(settings.getPasscodeHash()?.startsWith("v2:") == true)

            settings.setBiometricUnlockEnabled(true)
            viewModel.onEvent(SettingChangeEvent.AppLockChanged(false, "wrong"))
            advanceUntilIdle()
            assertTrue(settings.isAppLockEnabled())
            assertTrue(settings.isBiometricUnlockEnabled())
            assertTrue(viewModel.uiState.value.securityError?.isNotBlank() == true)

            viewModel.clearSecurityError()
            viewModel.onEvent(SettingChangeEvent.AppLockChanged(false, "secret"))
            advanceUntilIdle()
            assertFalse(settings.isAppLockEnabled())
            assertFalse(settings.isBiometricUnlockEnabled())
            assertEquals(null, settings.getPasscodeHash())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun testPasscodeManager() = PasscodeManager(
        deriveKey = { password, salt, size ->
            ByteArray(size) { index ->
                ((password[index % password.size].toInt() + salt[index % salt.size].toInt()) and 0x7f)
                    .toByte()
            }
        },
        generateSalt = { size -> ByteArray(size) { 7 } },
    )
}
