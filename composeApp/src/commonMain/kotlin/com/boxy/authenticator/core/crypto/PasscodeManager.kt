package com.boxy.authenticator.core.crypto

import diglol.crypto.random.nextBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException

class PasscodeManager internal constructor(
    private val deriveKey: CryptoKeyDeriver,
    private val generateSalt: (Int) -> ByteArray,
) {
    constructor() : this(
        deriveKey = { password, salt, size ->
            HashKeyGenerator.generateHashKey(password, salt, size)
        },
        generateSalt = ::nextBytes,
    )

    enum class Verification {
        CURRENT,
        LEGACY,
        INVALID,
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun create(password: String): String {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Password must contain at least $MIN_PASSWORD_LENGTH characters."
        }
        val passwordBytes = password.encodeToByteArray()
        val salt = generateSalt(SALT_SIZE)
        require(salt.size == SALT_SIZE) { "Invalid passcode salt size." }
        val hash = try {
            deriveKey(passwordBytes, salt, HASH_SIZE)
        } finally {
            passwordBytes.fill(0)
        }
        require(hash.size == HASH_SIZE) { "Invalid passcode hash size." }

        return try {
            listOf(FORMAT_VERSION, Base64.encode(salt), Base64.encode(hash)).joinToString(SEPARATOR)
        } finally {
            salt.fill(0)
            hash.fill(0)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun verify(password: String, storedCredential: String?): Verification {
        if (password.isEmpty() || storedCredential.isNullOrBlank()) return Verification.INVALID

        val parts = storedCredential.split(SEPARATOR)
        if (parts.firstOrNull() != FORMAT_VERSION) {
            return verifyLegacy(password, storedCredential)
        }
        if (parts.size != 3) return Verification.INVALID

        val salt = runCatching { Base64.decode(parts[1]) }.getOrNull()
            ?.takeIf { it.size == SALT_SIZE }
            ?: return Verification.INVALID
        val expectedHash = runCatching { Base64.decode(parts[2]) }.getOrNull()
            ?.takeIf { it.size == HASH_SIZE }
            ?: return Verification.INVALID
        val passwordBytes = password.encodeToByteArray()
        val actualHash = try {
            deriveKey(passwordBytes, salt, HASH_SIZE)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return Verification.INVALID
        } finally {
            passwordBytes.fill(0)
            salt.fill(0)
        }

        return try {
            if (constantTimeEquals(actualHash, expectedHash)) Verification.CURRENT
            else Verification.INVALID
        } finally {
            actualHash.fill(0)
            expectedHash.fill(0)
        }
    }

    private suspend fun verifyLegacy(password: String, storedCredential: String): Verification {
        val passwordBytes = password.encodeToByteArray()
        val legacyHash = try {
            deriveKey(passwordBytes, ByteArray(SALT_SIZE), HASH_SIZE).decodeToString()
                .encodeToByteArray()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return Verification.INVALID
        } finally {
            passwordBytes.fill(0)
        }
        val storedBytes = storedCredential.encodeToByteArray()
        return try {
            if (constantTimeEquals(legacyHash, storedBytes)) Verification.LEGACY
            else Verification.INVALID
        } finally {
            legacyHash.fill(0)
            storedBytes.fill(0)
        }
    }

    private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean {
        var difference = first.size xor second.size
        val comparedSize = maxOf(first.size, second.size)
        for (index in 0 until comparedSize) {
            val firstByte = if (index < first.size) first[index].toInt() else 0
            val secondByte = if (index < second.size) second[index].toInt() else 0
            difference = difference or (firstByte xor secondByte)
        }
        return difference == 0
    }

    private companion object {
        const val FORMAT_VERSION = "v2"
        const val SEPARATOR = ":"
        const val SALT_SIZE = 32
        const val HASH_SIZE = 32
        const val MIN_PASSWORD_LENGTH = 6
    }
}
