package com.boxy.authenticator.core.otp

import kotlin.math.floor

object OtpGenerator {

    /**
     * Generate a TOTP (Time-based OTP).
     *
     * @param secret The secret key as a ByteArray.
     * @param algo The hashing algorithm (HMAC-SHA1, HMAC-SHA256, etc.).
     * @param period The time step in seconds.
     * @return Generated OTP.
     */
    fun generateTotp(secret: ByteArray, algo: String, period: Long, time: Long): Int {
        val currentTime = time / 1000
        val counter = floor(currentTime.toDouble() / period).toLong()
        return generateHotp(secret, algo, counter)
    }

    /**
     * Generate an HOTP (Counter-based OTP).
     *
     * @param secret The secret key as a ByteArray.
     * @param algo The hashing algorithm (HMAC-SHA1, HMAC-SHA256, etc.).
     * @param counter The moving factor (incrementing counter).
     * @return Generated OTP.
     */
    fun generateHotp(secret: ByteArray, algo: String, counter: Long): Int {
        val hash = getHash(secret, algo, counter)
        val otp = truncateHash(hash)
        return otp
    }

    /**
     * Extracts the OTP value from the hashed byte array.
     */
    private fun truncateHash(hash: ByteArray): Int {
        val offset = hash[hash.size - 1].toInt() and 0xf
        return ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
    }
}

internal expect fun getHash(secret: ByteArray, algo: String, counter: Long): ByteArray
