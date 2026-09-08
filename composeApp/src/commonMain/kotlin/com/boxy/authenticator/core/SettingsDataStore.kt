package com.boxy.authenticator.core

import com.boxy.authenticator.data.preferences.PreferenceStore
import com.boxy.authenticator.domain.models.enums.AppTheme
import com.boxy.authenticator.domain.models.enums.TokenTapResponse
import com.boxy.authenticator.domain.models.AppSettings

class SettingsDataStore(
    private val store: PreferenceStore,
) {

    fun getSettings(): AppSettings {
        return AppSettings(
            // Appearance
            appTheme = getAppTheme(),

            // General
            tokenTapResponse = getTokenTapResponse(),
            isLockscreenPinPadEnabled = isLockscreenPinPadEnabled(),
            isDisableBackupAlertsEnabled = isDisableBackupAlertsEnabled(),

            // Security
            isAppLockEnabled = isAppLockEnabled(),
            isBiometricUnlockEnabled = isBiometricUnlockEnabled(),
            isBlockScreenshotsEnabled = isBlockScreenshotsEnabled(),
            isLockSensitiveFieldsEnabled = isLockSensitiveFieldsEnabled(),

            // Other
            lastBackupTimestamp = getLastBackupTimestamp()
        )
    }

    fun setAppTheme(theme: AppTheme) {
        store.putString(Keys.APP_THEME, theme.name)
    }

    fun getAppTheme(): AppTheme {
        val themeName = store.getString(Keys.APP_THEME)
        return try {
            AppTheme.valueOf(themeName ?: Defaults.APP_THEME.name)
        } catch (e: IllegalArgumentException) {
            Defaults.APP_THEME
        }
    }

    fun setLockscreenPinPadEnabled(isEnabled: Boolean) {
        store.putBoolean(Keys.LOCKSCREEN_PIN_PAD, isEnabled)
    }

    fun isLockscreenPinPadEnabled(default: Boolean = Defaults.LOCKSCREEN_PIN_PAD): Boolean {
        return try {
            store.getBoolean(Keys.LOCKSCREEN_PIN_PAD, default)
        } catch (e: Exception) {
            default
        }
    }

    fun setDisableBackupAlertsEnabled(isEnabled: Boolean) {
        store.putBoolean(Keys.DISABLE_BACKUP_ALERTS, isEnabled)
    }

    fun isDisableBackupAlertsEnabled(default: Boolean = Defaults.DISABLE_BACKUP_ALERTS): Boolean {
        return try {
            store.getBoolean(Keys.DISABLE_BACKUP_ALERTS, default)
        } catch (e: Exception) {
            default
        }
    }

    fun setTokenTapResponse(response: TokenTapResponse) {
        store.putString(Keys.TOKEN_TAP_RESPONSE, response.name)
    }

    fun getTokenTapResponse(): TokenTapResponse {
        val themeName = store.getString(Keys.TOKEN_TAP_RESPONSE)
        return try {
            TokenTapResponse.valueOf(themeName ?: Defaults.TOKEN_TAP_RESPONSE.name)
        } catch (e: IllegalArgumentException) {
            Defaults.TOKEN_TAP_RESPONSE
        }
    }

    fun setAppLockEnabled(isEnabled: Boolean, passwordHash: String = "") {
        store.putBoolean(Keys.APP_LOCK, isEnabled)
        if (!isEnabled) {
            store.remove(Keys.APP_LOCK_HASH)
        } else {
            if (passwordHash.isEmpty()) throw IllegalArgumentException("Password hash is empty")
            store.putString(Keys.APP_LOCK_HASH, passwordHash)
        }
    }

    fun isAppLockEnabled(default: Boolean = Defaults.APP_LOCK): Boolean {
        return try {
            store.getBoolean(Keys.APP_LOCK, default)
        } catch (e: Exception) {
            default
        }
    }

    fun setBiometricUnlockEnabled(isEnabled: Boolean) {
        store.putBoolean(Keys.BIOMETRIC_UNLOCK, isEnabled)
    }

    fun isBiometricUnlockEnabled(default: Boolean = Defaults.BIOMETRIC_UNLOCK): Boolean {
        return try {
            store.getBoolean(Keys.BIOMETRIC_UNLOCK, default)
        } catch (e: Exception) {
            default
        }
    }

    fun setBlockScreenshotsEnabled(block: Boolean) {
        store.putBoolean(Keys.BLOCK_SCREENSHOTS, block)
    }

    fun isBlockScreenshotsEnabled(default: Boolean = Defaults.BLOCK_SCREENSHOTS): Boolean {
        return try {
            store.getBoolean(Keys.BLOCK_SCREENSHOTS, default)
        } catch (e: Exception) {
            default
        }
    }

    fun setLockSensitiveFieldsEnabled(enabled: Boolean) {
        store.putBoolean(Keys.LOCK_SENSITIVE_FIELDS, enabled)
    }

    fun isLockSensitiveFieldsEnabled(default: Boolean = Defaults.LOCK_SENSITIVE_FIELDS): Boolean {
        return try {
            store.getBoolean(Keys.LOCK_SENSITIVE_FIELDS, default)
        } catch (e: Exception) {
            default
        }
    }

    fun getPasscodeHash(): String? {
        return store.getString(Keys.APP_LOCK_HASH, null)
    }

    fun markItemAsViewed(itemId: String) {
        val viewedItems = getViewedItems()
        if (itemId !in viewedItems) {
            saveViewedItems(viewedItems + itemId)
        }
    }

    private fun saveViewedItems(items: List<String>) {
        store.putString(Keys.VIEWED_ITEMS_LIST, items.joinToString(","))
    }

    fun getViewedItems(): List<String> {
        return store.getString(Keys.VIEWED_ITEMS_LIST)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: Defaults.VIEWED_ITEMS_LIST
    }

    fun setLastBackupTimestamp(timestamp: Long) {
        store.putLong(Keys.LAST_BACKUP_TIMESTAMP, timestamp)
    }

    fun getLastBackupTimestamp(): Long {
        return store.getLong(Keys.LAST_BACKUP_TIMESTAMP)
    }

    companion object {
        object Keys {
            // Appearance
            const val APP_THEME = "key_app_theme"

            // General
            const val TOKEN_TAP_RESPONSE = "key_token_tap_response"
            const val LOCKSCREEN_PIN_PAD = "key_lockscreen_pin_pad"
            const val DISABLE_BACKUP_ALERTS = "key_disable_backup_alerts"

            // Security
            const val APP_LOCK = "key_app_lock"
            const val APP_LOCK_HASH = "key_app_lock_hash"
            const val BIOMETRIC_UNLOCK = "key_biometric_unlock"
            const val BLOCK_SCREENSHOTS = "key_block_screenshots"
            const val LOCK_SENSITIVE_FIELDS = "key_lock_sensitive_fields"

            // Other
            const val VIEWED_ITEMS_LIST = "viewed_items_list"
            const val LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
        }

        object Defaults {
            // Appearance
            val APP_THEME = AppTheme.SYSTEM

            // General
            val TOKEN_TAP_RESPONSE = TokenTapResponse.NEVER
            const val LOCKSCREEN_PIN_PAD = false
            const val DISABLE_BACKUP_ALERTS = false

            // Security
            const val APP_LOCK = false
            const val BIOMETRIC_UNLOCK = false
            const val BLOCK_SCREENSHOTS = true
            const val LOCK_SENSITIVE_FIELDS = true

            val VIEWED_ITEMS_LIST = emptyList<String>()
        }
    }
}
