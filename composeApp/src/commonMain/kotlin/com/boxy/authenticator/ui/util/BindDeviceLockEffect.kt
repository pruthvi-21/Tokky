package com.boxy.authenticator.ui.util

import androidx.compose.runtime.Composable

/** Invokes [onDeviceLocked] when the operating system locks the device. */
@Composable
expect fun BindDeviceLockEffect(onDeviceLocked: () -> Unit)
