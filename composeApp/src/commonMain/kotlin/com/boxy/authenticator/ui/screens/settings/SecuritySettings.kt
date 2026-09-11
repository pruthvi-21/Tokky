package com.boxy.authenticator.ui.screens.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.biometric_prompt_title
import boxy_authenticator.composeapp.generated.resources.cancel
import boxy_authenticator.composeapp.generated.resources.enter_correct_password
import boxy_authenticator.composeapp.generated.resources.password
import boxy_authenticator.composeapp.generated.resources.preference_category_title_security
import boxy_authenticator.composeapp.generated.resources.preference_summary_app_lock
import boxy_authenticator.composeapp.generated.resources.preference_summary_biometrics
import boxy_authenticator.composeapp.generated.resources.preference_summary_block_screenshots
import boxy_authenticator.composeapp.generated.resources.preference_summary_lock_sensitive_fields
import boxy_authenticator.composeapp.generated.resources.preference_title_app_lock
import boxy_authenticator.composeapp.generated.resources.preference_title_biometrics
import boxy_authenticator.composeapp.generated.resources.preference_title_block_screenshots
import boxy_authenticator.composeapp.generated.resources.preference_title_lock_sensitive_fields
import boxy_authenticator.composeapp.generated.resources.remove_password
import boxy_authenticator.composeapp.generated.resources.to_disable_this_setting
import boxy_authenticator.composeapp.generated.resources.to_enable_biometrics
import boxy_authenticator.composeapp.generated.resources.verify_your_identity
import com.boxy.authenticator.core.BiometricsHelper
import com.boxy.authenticator.core.Platform
import com.boxy.authenticator.domain.models.form.SettingChangeEvent
import com.boxy.authenticator.ui.components.dialogs.RequestPasswordDialog
import com.boxy.authenticator.ui.components.dialogs.SetPasswordDialog
import com.boxy.authenticator.ui.state.SettingsUiState
import com.jw.preferences.PreferenceCategory
import com.jw.preferences.SwitchPreference
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SecuritySettings(
    uiState: SettingsUiState,
    onEvent: (SettingChangeEvent) -> Unit,
    showEnableAppLockDialog: (Boolean) -> Unit,
    showDisableAppLockDialog: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val biometricsHelper: BiometricsHelper = koinInject()

    val isAppLockEnabled = uiState.settings.isAppLockEnabled
    val isBiometricUnlockEnabled = uiState.settings.isBiometricUnlockEnabled
    val isBlockScreenshotsEnabled = uiState.settings.isBlockScreenshotsEnabled
    val isLockSensitiveFieldsEnabled = uiState.settings.isLockSensitiveFieldsEnabled

    val scope = rememberCoroutineScope()

    PreferenceCategory(
        title = { Text(stringResource(Res.string.preference_category_title_security)) },
    ) {
        SwitchPreference(
            title = { Text(stringResource(Res.string.preference_title_app_lock)) },
            summary = { Text(stringResource(Res.string.preference_summary_app_lock)) },
            value = isAppLockEnabled,
            onValueChange = { newValue ->
                if (newValue) {
                    showEnableAppLockDialog(true)
                    showDisableAppLockDialog(false)
                } else {
                    showEnableAppLockDialog(false)
                    showDisableAppLockDialog(true)
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
            },
        )
        SwitchPreference(
            title = { Text(stringResource(Res.string.preference_title_biometrics)) },
            summary = { Text(stringResource(Res.string.preference_summary_biometrics)) },
            enabled = uiState.settings.isAppLockEnabled &&
                    (isBiometricUnlockEnabled || biometricsHelper.isBiometricAvailable()),
            value = isBiometricUnlockEnabled,
            onValueChange = { newValue ->
                scope.launch {
                    // Removing an unlock method is always safe. Adding one requires a successful
                    // biometric check so the toggle cannot be enabled by an unauthenticated tap.
                    val canProceed = !newValue || biometricsHelper.promptForBiometrics(
                                title = getString(Res.string.biometric_prompt_title),
                                reason = getString(Res.string.to_enable_biometrics),
                                failureButtonText = getString(Res.string.cancel),
                            )

                    if (canProceed) {
                        onEvent(SettingChangeEvent.BiometricUnlockChanged(newValue))
                    }
                }
            },
        )
        if (Platform.isAndroid) {
            SwitchPreference(
                title = { Text(stringResource(Res.string.preference_title_block_screenshots)) },
                summary = { Text(stringResource(Res.string.preference_summary_block_screenshots)) },
                value = isBlockScreenshotsEnabled,
                onValueChange = {
                    onEvent(SettingChangeEvent.BlockScreenshotsChanged(it))
                },
            )
        }
        SwitchPreference(
            title = { Text(stringResource(Res.string.preference_title_lock_sensitive_fields)) },
            summary = { Text(stringResource(Res.string.preference_summary_lock_sensitive_fields)) },
            value = isLockSensitiveFieldsEnabled,
            onValueChange = { newValue ->
                scope.launch {
                    if (newValue) {
                        onEvent(SettingChangeEvent.LockSensitiveFieldsChanged(true))
                        return@launch
                    }

                    val canProceed = biometricsHelper.isBiometricAvailable()
                        .not() || biometricsHelper.promptForBiometrics(
                        title = getString(Res.string.verify_your_identity),
                        reason = getString(Res.string.to_disable_this_setting),
                        failureButtonText = getString(Res.string.cancel),
                    )

                    if (canProceed) {
                        onEvent(SettingChangeEvent.LockSensitiveFieldsChanged(false))
                    }
                }
            },
            showDivider = false,
        )
    }

    if (uiState.showEnableAppLockDialog) {
        SetPasswordDialog(
            numericOnly = uiState.settings.isLockscreenPinPadEnabled,
            onDismissRequest = {
                showEnableAppLockDialog(false)
            },
            onConfirmation = { password ->
                showEnableAppLockDialog(false)
                onEvent(SettingChangeEvent.AppLockChanged(true, password))
            }
        )
    }

    if (uiState.showDisableAppLockDialog) {
        RequestPasswordDialog(
            title = stringResource(Res.string.remove_password),
            label = stringResource(Res.string.password),
            placeholder = stringResource(Res.string.enter_correct_password),
            onDismissRequest = {
                showDisableAppLockDialog(false)
            },
            onConfirmation = { password ->
                showDisableAppLockDialog(false)

                onEvent(SettingChangeEvent.AppLockChanged(false, password))
            }
        )
    }
}
