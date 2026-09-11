package com.boxy.authenticator.navigation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class AppLockLifecycleGuard(
    private val isAppLockEnabled: () -> Boolean,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val backgroundGracePeriod: Duration = 60.seconds,
) {
    private var backgroundedAt: TimeMark? = null

    fun onStopped() {
        backgroundedAt = if (isAppLockEnabled()) timeSource.markNow() else null
    }

    fun onStarted(): Boolean {
        val elapsed = backgroundedAt?.elapsedNow()
        backgroundedAt = null
        val shouldRelock = isAppLockEnabled() && elapsed != null &&
                elapsed > backgroundGracePeriod
        return shouldRelock
    }

    fun onDeviceLocked(): Boolean = isAppLockEnabled()
}
