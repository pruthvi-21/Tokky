package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.incorrect_password
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.crypto.PasscodeManager
import com.boxy.authenticator.domain.models.form.SettingChangeEvent
import com.boxy.authenticator.domain.models.AppSettings
import com.boxy.authenticator.ui.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getString

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val passcodeManager: PasscodeManager,
) : ViewModel() {
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

            is SettingChangeEvent.LabelVisibilityChanged -> {
                settingsDataStore.setLabelVisibility(event.visibility)
                updateSettings(
                    _uiState.value.settings.copy(labelVisibility = event.visibility)
                )
            }

            is SettingChangeEvent.ShowLabelCountsChanged -> {
                settingsDataStore.setShowLabelCountsEnabled(event.enabled)
                updateSettings(
                    _uiState.value.settings.copy(isShowLabelCountsEnabled = event.enabled)
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
                if (_uiState.value.isSecurityOperationInProgress) return
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        isSecurityOperationInProgress = true,
                        securityError = null,
                    )
                    try {
                        if (event.enabled) {
                            val credential = passcodeManager.create(event.password)
                            settingsDataStore.setAppLockEnabled(true, credential)
                        } else {
                            val verification = passcodeManager.verify(
                                event.password,
                                settingsDataStore.getPasscodeHash(),
                            )
                            if (verification == PasscodeManager.Verification.INVALID) {
                                _uiState.value = _uiState.value.copy(
                                    securityError = incorrectPasswordMessage(),
                                )
                                return@launch
                            }
                            settingsDataStore.setAppLockEnabled(false)
                        }
                        updateSettings(settingsDataStore.getSettings())
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        _uiState.value = _uiState.value.copy(
                            securityError = error.message ?: "Unable to update app lock",
                        )
                    } finally {
                        _uiState.value = _uiState.value.copy(isSecurityOperationInProgress = false)
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

    fun clearSecurityError() {
        _uiState.value = _uiState.value.copy(securityError = null)
    }

    private suspend fun incorrectPasswordMessage(): String = try {
        getString(Res.string.incorrect_password)
    } catch (_: Exception) {
        "Incorrect password"
    }
}
