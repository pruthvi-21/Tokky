package com.boxy.authenticator.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class AppLockLifecycleGuardTest {
    @Test
    fun `background grace period does not lock before sixty seconds`() {
        var enabled = true
        val time = TestTimeSource()
        val guard = AppLockLifecycleGuard({ enabled }, time)

        guard.onStopped()
        time += 59.seconds
        assertFalse(guard.onStarted())
    }

    @Test
    fun `exactly sixty seconds remains inside the grace period`() {
        val time = TestTimeSource()
        val guard = AppLockLifecycleGuard({ true }, time)

        guard.onStopped()
        time += 60.seconds
        assertFalse(guard.onStarted())
    }

    @Test
    fun `backgrounding for more than sixty seconds requests authentication`() {
        val time = TestTimeSource()
        val guard = AppLockLifecycleGuard({ true }, time)

        guard.onStopped()
        time += 60.seconds + 1.milliseconds
        assertTrue(guard.onStarted())
    }

    @Test
    fun `disabled lock never creates a stale relock request`() {
        var enabled = false
        val guard = AppLockLifecycleGuard({ enabled }, TestTimeSource())

        guard.onStopped()
        enabled = true
        assertFalse(guard.onStarted())
    }

    @Test
    fun `disabling lock while backgrounded cancels pending relock`() {
        var enabled = true
        val guard = AppLockLifecycleGuard({ enabled }, TestTimeSource())

        guard.onStopped()
        enabled = false
        assertFalse(guard.onStarted())
    }

    @Test
    fun `device lock requests authentication immediately`() {
        var enabled = true
        val guard = AppLockLifecycleGuard(isAppLockEnabled = { enabled })

        assertTrue(guard.onDeviceLocked())
        enabled = false
        assertFalse(guard.onDeviceLocked())
    }
}
