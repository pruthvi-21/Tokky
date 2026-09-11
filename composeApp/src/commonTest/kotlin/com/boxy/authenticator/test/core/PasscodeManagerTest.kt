package com.boxy.authenticator.test.core

import com.boxy.authenticator.core.crypto.PasscodeManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

class PasscodeManagerTest {
    @Test
    fun `current credentials accept only the correct password`() = runTest {
        val manager = managerWithSalts(1)
        val credential = manager.create("correct horse")

        assertEquals(PasscodeManager.Verification.CURRENT, manager.verify("correct horse", credential))
        assertEquals(PasscodeManager.Verification.INVALID, manager.verify("wrong", credential))
        assertFalse(credential.contains("correct horse"))
    }

    @Test
    fun `a unique salt produces a unique credential for the same password`() = runTest {
        val first = managerWithSalts(1).create("same password")
        val second = managerWithSalts(2).create("same password")

        assertNotEquals(first, second)
    }

    @Test
    fun `malformed and unsupported credentials fail closed`() = runTest {
        val manager = managerWithSalts(1)

        listOf(null, "", "v2", "v2:not-base64:also-not-base64", "v3:a:b").forEach {
            assertEquals(PasscodeManager.Verification.INVALID, manager.verify("password", it))
        }
    }

    @Test
    fun `legacy zero-salt credential is recognized for migration`() = runTest {
        val manager = managerWithSalts(1)
        val legacyBytes = derive("old password".encodeToByteArray(), ByteArray(32), 32)
        val legacyCredential = legacyBytes.decodeToString()

        assertEquals(
            PasscodeManager.Verification.LEGACY,
            manager.verify("old password", legacyCredential),
        )
        assertEquals(PasscodeManager.Verification.INVALID, manager.verify("wrong", legacyCredential))
    }

    @Test
    fun `credential creation enforces the minimum passcode length`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            managerWithSalts(1).create("12345")
        }
    }

    private fun managerWithSalts(value: Byte) = PasscodeManager(
        deriveKey = ::derive,
        generateSalt = { size -> ByteArray(size) { value } },
    )

    private fun derive(password: ByteArray, salt: ByteArray, size: Int): ByteArray =
        ByteArray(size) { index ->
            val passwordByte = password[index % password.size].toInt() and 0xff
            val saltByte = salt[index % salt.size].toInt() and 0xff
            // Keep output valid UTF-8 so the legacy-format fixture is deterministic.
            ('A'.code + ((passwordByte + saltByte + index) % 26)).toByte()
        }
}
