package com.boxy.authenticator.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.boxy.authenticator.domain.models.AppLocale

actual object AppLocaleManager {
    actual val isOverrideSupported: Boolean = true

    actual fun setAppLocale(locale: AppLocale) {
        val locales = if (locale.isSystemDefault) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(locale.localeTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
