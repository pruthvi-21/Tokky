package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.crypto.HashKeyGenerator
import com.boxy.authenticator.domain.models.form.SettingChangeEvent
import com.boxy.authenticator.domain.models.AppSettings
import com.boxy.authenticator.ui.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val logger = Logger("SettingsViewModel")

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        updateSettings(settingsDataStore.getSettings())
    }

    fun onEvent(event: SettingChangeEvent) {
        when (event) {
            is SettingChangeEvent.AppThemeChanged -> {
                settingsDataStore.setAppTheme(event.theme)
                updateSettings(
                    _uiState.value.settings.copy(appTheme = event.theme)
                )
            }

            is SettingChangeEvent.TokenTapResponseChanged -> {
                settingsDataStore.setTokenTapResponse(event.response)
                updateSettings(
                    _uiState.value.settings.copy(tokenTapResponse = event.response)
                )
            }

            is SettingChangeEvent.LockScreenPinPadChanged -> {
                settingsDataStore.setLockscreenPinPadEnabled(event.enabled)
                updateSettings(
                    _uiState.value.settings.copy(isLockscreenPinPadEnabled = event.enabled)
                )
            }

            is SettingChangeEvent.BackupAlertsChanged -> {
                settingsDataStore.setDisableBackupAlertsEnabled(event.enabled)
                updateSettings(
                    _uiState.value.settings.copy(isDisableBackupAlertsEnabled = event.enabled)
                )
            }

            is SettingChangeEvent.AppLockChanged -> {
                viewModelScope.launch {
                    val hash = HashKeyGenerator.generateHashKey(event.password)
                    if (event.enabled) {
                        settingsDataStore.setAppLockEnabled(true, hash)
                        updateSettings(
                            _uiState.value.settings.copy(isAppLockEnabled = true)
                        )
                    } else {
                        val storedHash = settingsDataStore.getPasscodeHash()
                        val isValid = hash.contentEquals(storedHash)

                        if (isValid) {
                            settingsDataStore.setAppLockEnabled(false)
                            settingsDataStore.setBiometricUnlockEnabled(false)

                            updateSettings(
                                _uiState.value.settings.copy(
                                    isAppLockEnabled = false,
                                    isBiometricUnlockEnabled = false,
                                )
                            )
                        } else {
                            throw Exception()
                        }
                    }
                }
            }

            is SettingChangeEvent.BiometricUnlockChanged -> {
                settingsDataStore.setBiometricUnlockEnabled(event.enabled)
                updateSettings(
                    _uiState.value.settings.copy(isBiometricUnlockEnabled = event.enabled)
                )
            }
            is SettingChangeEvent.BlockScreenshotsChanged -> {
                settingsDataStore.setBlockScreenshotsEnabled(event.enabled)
                updateSettings(
                    _uiState.value.settings.copy(isBlockScreenshotsEnabled = event.enabled)
                )
            }

            is SettingChangeEvent.LockSensitiveFieldsChanged -> {
                settingsDataStore.setLockSensitiveFieldsEnabled(event.enabled)
                updateSettings(
                    _uiState.value.settings.copy(isLockSensitiveFieldsEnabled = event.enabled)
                )
            }
        }
    }

    private fun updateSettings(settings: AppSettings) {
        _uiState.value = _uiState.value.copy(
            settings = settings
        )
    }

    fun showEnableAppLockDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showEnableAppLockDialog = show
        )
    }

    fun showDisableAppLockDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showDisableAppLockDialog = show
        )
    }
}
