package com.boxy.authenticator.core

import com.boxy.authenticator.data.preferences.PreferenceStore
import com.boxy.authenticator.domain.models.AppLocale
import com.boxy.authenticator.domain.models.AppLocales
import com.boxy.authenticator.domain.models.enums.AppTheme
import com.boxy.authenticator.domain.models.enums.LabelVisibility
import com.boxy.authenticator.domain.models.enums.TokenTapResponse
import com.boxy.authenticator.domain.models.AppSettings

class SettingsDataStore(
    private val store: PreferenceStore,
) {

    fun getSettings(): AppSettings {
        return AppSettings(
            // Appearance
            appTheme = getAppTheme(),
            appLocale = getAppLocale(),

            // General
            tokenTapResponse = getTokenTapResponse(),
            labelVisibility = getLabelVisibility(),
            isShowLabelCountsEnabled = isShowLabelCountsEnabled(),
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

    fun setAppLocale(locale: AppLocale) {
        store.putString(Keys.APP_LOCALE, locale.localeTag)
        AppLocaleManager.setAppLocale(locale)
    }

    fun getAppLocale(): AppLocale {
        val localeTag = store.getString(Keys.APP_LOCALE).orEmpty()
        val locale = if (localeTag.isBlank()) {
            AppLocale.SystemDefault
        } else {
            AppLocales.firstOrNull { it.matches(localeTag) } ?: AppLocale.SystemDefault
        }
        AppLocaleManager.setAppLocale(locale)
        return locale
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

    fun setLabelVisibility(visibility: LabelVisibility) {
        store.putString(Keys.LABEL_VISIBILITY, visibility.name)
    }

    fun getLabelVisibility(): LabelVisibility {
        val visibilityName = store.getString(Keys.LABEL_VISIBILITY)
        return try {
            LabelVisibility.valueOf(visibilityName ?: Defaults.LABEL_VISIBILITY.name)
        } catch (_: IllegalArgumentException) {
            Defaults.LABEL_VISIBILITY
        }
    }

    fun setDefaultLabelFilter(label: String?) {
        if (label == null) store.remove(Keys.DEFAULT_LABEL_FILTER)
        else store.putString(Keys.DEFAULT_LABEL_FILTER, label)
    }

    fun getDefaultLabelFilter(): String? = store.getString(Keys.DEFAULT_LABEL_FILTER)

    fun setShowLabelCountsEnabled(enabled: Boolean) {
        store.putBoolean(Keys.SHOW_LABEL_COUNTS, enabled)
    }

    fun isShowLabelCountsEnabled(default: Boolean = Defaults.SHOW_LABEL_COUNTS): Boolean {
        return try {
            store.getBoolean(Keys.SHOW_LABEL_COUNTS, default)
        } catch (_: Exception) {
            default
        }
    }

    fun setAppLockEnabled(isEnabled: Boolean, passwordHash: String = "") {
        if (!isEnabled) {
            store.putBoolean(Keys.APP_LOCK, false)
            store.putBoolean(Keys.BIOMETRIC_UNLOCK, false)
            store.remove(Keys.APP_LOCK_HASH)
        } else {
            require(passwordHash.isNotBlank()) { "Password hash is empty" }
            // Persist the credential first so a failed write can never enable an unusable lock.
            store.putString(Keys.APP_LOCK_HASH, passwordHash)
            store.putBoolean(Keys.APP_LOCK, true)
        }
    }

    fun isAppLockEnabled(default: Boolean = Defaults.APP_LOCK): Boolean {
        return try {
            store.getBoolean(Keys.APP_LOCK, default) && !getPasscodeHash().isNullOrBlank()
        } catch (e: Exception) {
            default
        }
    }

    fun setBiometricUnlockEnabled(isEnabled: Boolean) {
        require(!isEnabled || (isAppLockEnabled() && !getPasscodeHash().isNullOrBlank())) {
            "Biometric unlock requires an enabled app lock"
        }
        store.putBoolean(Keys.BIOMETRIC_UNLOCK, isEnabled)
    }

    fun isBiometricUnlockEnabled(default: Boolean = Defaults.BIOMETRIC_UNLOCK): Boolean {
        if (!isAppLockEnabled() || getPasscodeHash().isNullOrBlank()) return false
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

    fun updatePasscodeHash(passwordHash: String) {
        require(isAppLockEnabled()) { "App lock is disabled" }
        require(passwordHash.isNotBlank()) { "Password hash is empty" }
        store.putString(Keys.APP_LOCK_HASH, passwordHash)
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
            const val APP_LOCALE = "key_app_locale"

            // General
            const val TOKEN_TAP_RESPONSE = "key_token_tap_response"
            const val LABEL_VISIBILITY = "key_label_visibility"
            const val DEFAULT_LABEL_FILTER = "key_default_label_filter"
            const val SHOW_LABEL_COUNTS = "key_show_label_counts"
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
            val LABEL_VISIBILITY = LabelVisibility.ALWAYS
            const val SHOW_LABEL_COUNTS = false
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
