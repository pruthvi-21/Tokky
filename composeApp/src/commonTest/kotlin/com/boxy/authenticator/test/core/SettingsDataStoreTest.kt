package com.boxy.authenticator.test.core

import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.models.enums.AppTheme
import com.boxy.authenticator.domain.models.enums.TokenTapResponse
import com.boxy.authenticator.test.InMemoryPreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsDataStoreTest {
    private val store = InMemoryPreferenceStore()
    private val settings = SettingsDataStore(store)

    @Test
    fun `defaults are secure and stable`() {
        val result = settings.getSettings()

        assertEquals(AppTheme.SYSTEM, result.appTheme)
        assertEquals(TokenTapResponse.NEVER, result.tokenTapResponse)
        assertFalse(result.isAppLockEnabled)
        assertFalse(result.isBiometricUnlockEnabled)
        assertTrue(result.isBlockScreenshotsEnabled)
        assertTrue(result.isLockSensitiveFieldsEnabled)
        assertEquals(-1L, result.lastBackupTimestamp)
    }

    @Test
    fun `all ordinary settings round trip through preference store`() {
        settings.setAppTheme(AppTheme.DARK)
        settings.setTokenTapResponse(TokenTapResponse.DOUBLE_TAP)
        settings.setLockscreenPinPadEnabled(true)
        settings.setDisableBackupAlertsEnabled(true)
        settings.setBiometricUnlockEnabled(true)
        settings.setBlockScreenshotsEnabled(false)
        settings.setLockSensitiveFieldsEnabled(false)
        settings.setLastBackupTimestamp(42L)

        val result = settings.getSettings()
        assertEquals(AppTheme.DARK, result.appTheme)
        assertEquals(TokenTapResponse.DOUBLE_TAP, result.tokenTapResponse)
        assertTrue(result.isLockscreenPinPadEnabled)
        assertTrue(result.isDisableBackupAlertsEnabled)
        assertTrue(result.isBiometricUnlockEnabled)
        assertFalse(result.isBlockScreenshotsEnabled)
        assertFalse(result.isLockSensitiveFieldsEnabled)
        assertEquals(42L, result.lastBackupTimestamp)
    }

    @Test
    fun `unknown enum values fall back to defaults`() {
        store.values[SettingsDataStore.Companion.Keys.APP_THEME] = "FUTURE_THEME"
        store.values[SettingsDataStore.Companion.Keys.TOKEN_TAP_RESPONSE] = "TRIPLE_TAP"

        assertEquals(AppTheme.SYSTEM, settings.getAppTheme())
        assertEquals(TokenTapResponse.NEVER, settings.getTokenTapResponse())
    }

    @Test
    fun `enabling app lock requires and stores a password hash`() {
        assertFailsWith<IllegalArgumentException> { settings.setAppLockEnabled(true) }

        settings.setAppLockEnabled(true, "fake-hash")
        assertTrue(settings.isAppLockEnabled())
        assertEquals("fake-hash", settings.getPasscodeHash())
    }

    @Test
    fun `disabling app lock removes password hash`() {
        settings.setAppLockEnabled(true, "fake-hash")
        settings.setAppLockEnabled(false)

        assertFalse(settings.isAppLockEnabled())
        assertNull(settings.getPasscodeHash())
    }

    @Test
    fun `viewed items retain insertion order without duplicates`() {
        settings.markItemAsViewed("first")
        settings.markItemAsViewed("second")
        settings.markItemAsViewed("first")

        assertEquals(listOf("first", "second"), settings.getViewedItems())
    }

    @Test
    fun `boolean reads return caller fallback when store fails`() {
        store.readError = IllegalStateException("unavailable")

        assertTrue(settings.isAppLockEnabled(default = true))
        assertFalse(settings.isBlockScreenshotsEnabled(default = false))
    }
}
