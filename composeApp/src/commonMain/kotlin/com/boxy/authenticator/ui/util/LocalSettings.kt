package com.boxy.authenticator.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.boxy.authenticator.domain.models.AppSettings

val LocalSettings = staticCompositionLocalOf<AppSettings> {
    error("AppSettings not provided")
}
