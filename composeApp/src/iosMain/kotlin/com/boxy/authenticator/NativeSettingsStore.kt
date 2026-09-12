package com.boxy.authenticator

import com.boxy.authenticator.core.SettingsDataStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NativeSettingsStore : KoinComponent {
    private val settingsDataStore: SettingsDataStore by inject()

    init {
        NativeAppBootstrap.start()
    }

    fun snapshot(): NativeSettingsSnapshot {
        val settings = settingsDataStore.getSettings()
        return NativeSettingsSnapshot(
            showLabelCounts = settings.isShowLabelCountsEnabled,
            lockscreenPinPad = settings.isLockscreenPinPadEnabled,
            disableBackupAlerts = settings.isDisableBackupAlertsEnabled,
            blockScreenshots = settings.isBlockScreenshotsEnabled,
            lockSensitiveFields = settings.isLockSensitiveFieldsEnabled,
            appLockEnabled = settings.isAppLockEnabled,
            biometricUnlockEnabled = settings.isBiometricUnlockEnabled,
        )
    }

    fun setShowLabelCounts(enabled: Boolean) {
        settingsDataStore.setShowLabelCountsEnabled(enabled)
    }

    fun setLockscreenPinPad(enabled: Boolean) {
        settingsDataStore.setLockscreenPinPadEnabled(enabled)
    }

    fun setDisableBackupAlerts(enabled: Boolean) {
        settingsDataStore.setDisableBackupAlertsEnabled(enabled)
    }

    fun setBlockScreenshots(enabled: Boolean) {
        settingsDataStore.setBlockScreenshotsEnabled(enabled)
    }

    fun setLockSensitiveFields(enabled: Boolean) {
        settingsDataStore.setLockSensitiveFieldsEnabled(enabled)
    }
}

data class NativeSettingsSnapshot(
    val showLabelCounts: Boolean,
    val lockscreenPinPad: Boolean,
    val disableBackupAlerts: Boolean,
    val blockScreenshots: Boolean,
    val lockSensitiveFields: Boolean,
    val appLockEnabled: Boolean,
    val biometricUnlockEnabled: Boolean,
)
