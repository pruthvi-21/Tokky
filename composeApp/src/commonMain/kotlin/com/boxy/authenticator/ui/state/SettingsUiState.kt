package com.boxy.authenticator.ui.state

import com.boxy.authenticator.domain.models.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val showEnableAppLockDialog: Boolean = false,
    val showDisableAppLockDialog: Boolean = false,
    val securityError: String? = null,
    val isSecurityOperationInProgress: Boolean = false,
)
