package com.boxy.authenticator.core

import com.boxy.authenticator.domain.models.AppLocale

expect object AppLocaleManager {
    val isOverrideSupported: Boolean

    fun setAppLocale(locale: AppLocale)
}
