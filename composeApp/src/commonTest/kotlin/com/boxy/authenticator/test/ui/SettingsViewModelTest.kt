package com.boxy.authenticator.test.ui

import com.boxy.authenticator.core.SettingsDataStore
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

        val state = SettingsViewModel(settings).uiState.value.settings

        assertEquals(AppTheme.DARK, state.appTheme)
        assertEquals(TokenTapResponse.LONG_PRESS, state.tokenTapResponse)
        assertEquals(LabelVisibility.NEVER, state.labelVisibility)
        assertFalse(state.isBlockScreenshotsEnabled)
    }

    @Test
    fun `synchronous setting events update state and persistence`() {
        val store = InMemoryPreferenceStore()
        val settings = SettingsDataStore(store)
        val viewModel = SettingsViewModel(settings)

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
        val viewModel = SettingsViewModel(SettingsDataStore(InMemoryPreferenceStore()))

        viewModel.showEnableAppLockDialog(true)
        assertTrue(viewModel.uiState.value.showEnableAppLockDialog)
        assertFalse(viewModel.uiState.value.showDisableAppLockDialog)

        viewModel.showDisableAppLockDialog(true)
        viewModel.showEnableAppLockDialog(false)
        assertFalse(viewModel.uiState.value.showEnableAppLockDialog)
        assertTrue(viewModel.uiState.value.showDisableAppLockDialog)
    }
}
