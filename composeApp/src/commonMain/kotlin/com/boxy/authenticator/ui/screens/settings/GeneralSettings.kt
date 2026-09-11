package com.boxy.authenticator.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.always
import boxy_authenticator.composeapp.generated.resources.double_tap
import boxy_authenticator.composeapp.generated.resources.long_press
import boxy_authenticator.composeapp.generated.resources.never
import boxy_authenticator.composeapp.generated.resources.only_when_expanded
import boxy_authenticator.composeapp.generated.resources.preference_category_title_general
import boxy_authenticator.composeapp.generated.resources.preference_summary_disable_backup_alerts
import boxy_authenticator.composeapp.generated.resources.preference_summary_show_label_counts
import boxy_authenticator.composeapp.generated.resources.preference_summary_use_pin
import boxy_authenticator.composeapp.generated.resources.preference_title_disable_backup_alerts
import boxy_authenticator.composeapp.generated.resources.preference_title_label_visibility
import boxy_authenticator.composeapp.generated.resources.preference_title_show_label_counts
import boxy_authenticator.composeapp.generated.resources.preference_title_token_tap_response
import boxy_authenticator.composeapp.generated.resources.preference_title_use_pin
import boxy_authenticator.composeapp.generated.resources.single_tap
import com.boxy.authenticator.domain.models.enums.LabelVisibility
import com.boxy.authenticator.domain.models.enums.TokenTapResponse
import com.boxy.authenticator.domain.models.form.SettingChangeEvent
import com.boxy.authenticator.ui.state.SettingsUiState
import com.jw.preferences.DropDownPreference
import com.jw.preferences.PreferenceCategory
import com.jw.preferences.SwitchPreference
import org.jetbrains.compose.resources.stringResource

@Composable
fun GeneralSettings(
    uiState: SettingsUiState,
    onEvent: (SettingChangeEvent) -> Unit,
) {
    val isLockscreenPinPadEnabled = uiState.settings.isLockscreenPinPadEnabled
    val isDisableBackupAlertsEnabled = uiState.settings.isDisableBackupAlertsEnabled

    val tokenTapResponse = uiState.settings.tokenTapResponse
    val tokenTapResponseLabels = listOf(
        stringResource(Res.string.never),
        stringResource(Res.string.single_tap),
        stringResource(Res.string.double_tap),
        stringResource(Res.string.long_press),
    )
    val labelVisibility = uiState.settings.labelVisibility
    val labelVisibilityLabels = listOf(
        stringResource(Res.string.never),
        stringResource(Res.string.only_when_expanded),
        stringResource(Res.string.always),
    )

    PreferenceCategory(
        title = { Text(stringResource(Res.string.preference_category_title_general)) },
    ) {
        DropDownPreference(
            title = { Text(stringResource(Res.string.preference_title_token_tap_response)) },
            value = tokenTapResponseLabels[tokenTapResponse.ordinal],
            entries = tokenTapResponseLabels,
            summary = {
                Text(
                    text = tokenTapResponseLabels[tokenTapResponse.ordinal],
                    color = MaterialTheme.colorScheme.primary
                )
            },
            onValueChange = {
                val theme = when (it) {
                    tokenTapResponseLabels[1] -> TokenTapResponse.SINGLE_TAP
                    tokenTapResponseLabels[2] -> TokenTapResponse.DOUBLE_TAP
                    tokenTapResponseLabels[3] -> TokenTapResponse.LONG_PRESS
                    else -> TokenTapResponse.NEVER
                }
                onEvent(SettingChangeEvent.TokenTapResponseChanged(theme))
            },
        )
        DropDownPreference(
            title = { Text(stringResource(Res.string.preference_title_label_visibility)) },
            value = labelVisibilityLabels[labelVisibility.ordinal],
            entries = labelVisibilityLabels,
            summary = {
                Text(
                    text = labelVisibilityLabels[labelVisibility.ordinal],
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            onValueChange = {
                val visibility = when (it) {
                    labelVisibilityLabels[1] -> LabelVisibility.WHEN_EXPANDED
                    labelVisibilityLabels[2] -> LabelVisibility.ALWAYS
                    else -> LabelVisibility.NEVER
                }
                onEvent(SettingChangeEvent.LabelVisibilityChanged(visibility))
            },
        )
        SwitchPreference(
            title = { Text(stringResource(Res.string.preference_title_show_label_counts)) },
            summary = { Text(stringResource(Res.string.preference_summary_show_label_counts)) },
            value = uiState.settings.isShowLabelCountsEnabled,
            onValueChange = { onEvent(SettingChangeEvent.ShowLabelCountsChanged(it)) },
        )
        SwitchPreference(
            title = { Text(stringResource(Res.string.preference_title_use_pin)) },
            summary = { Text(stringResource(Res.string.preference_summary_use_pin)) },
            enabled = !uiState.settings.isAppLockEnabled,
            value = isLockscreenPinPadEnabled,
            onValueChange = {
                onEvent(SettingChangeEvent.LockScreenPinPadChanged(it))
            },
        )
        SwitchPreference(
            title = { Text(stringResource(Res.string.preference_title_disable_backup_alerts)) },
            summary = { Text(stringResource(Res.string.preference_summary_disable_backup_alerts)) },
            value = isDisableBackupAlertsEnabled,
            onValueChange = {
                onEvent(SettingChangeEvent.BackupAlertsChanged(it))
            },
            showDivider = false,
        )
    }
}
