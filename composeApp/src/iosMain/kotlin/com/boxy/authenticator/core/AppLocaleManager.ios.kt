package com.boxy.authenticator.core

import com.boxy.authenticator.domain.models.AppLocale

actual object AppLocaleManager {
    actual val isOverrideSupported: Boolean = false

    actual fun setAppLocale(locale: AppLocale) = Unit
}
