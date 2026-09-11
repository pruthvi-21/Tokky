package com.boxy.authenticator.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter

private const val PROTECTED_DATA_UNAVAILABLE_NOTIFICATION =
    "UIApplicationProtectedDataWillBecomeUnavailable"

@Composable
actual fun BindDeviceLockEffect(onDeviceLocked: () -> Unit) {
    val currentCallback by rememberUpdatedState(onDeviceLocked)

    DisposableEffect(Unit) {
        val notificationCenter = NSNotificationCenter.defaultCenter
        val observer = notificationCenter.addObserverForName(
            name = PROTECTED_DATA_UNAVAILABLE_NOTIFICATION,
            `object` = null,
            queue = null,
        ) { currentCallback() }
        onDispose { notificationCenter.removeObserver(observer) }
    }
}
