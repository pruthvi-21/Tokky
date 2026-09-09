package com.boxy.authenticator.core.crypto

import diglol.crypto.Hmac
import diglol.crypto.XChaCha20
import diglol.crypto.random.nextBytes

internal typealias CryptoKeyDeriver =
    suspend (password: ByteArray, salt: ByteArray, size: Int) -> ByteArray

object Crypto {

    private val MAGIC = "AUTHBACKUP".encodeToByteArray()
    private const val VERSION: Byte = 1
    private const val SALT_SIZE = 32
    private const val KEY_SIZE = 32
    private const val MAC_SIZE = 32

    /**
     * Encrypts and authenticates data in the versioned backup container.
     * A random salt makes password derivation unique for every backup. The MAC is verified before
     * any plaintext is returned, so wrong passwords and modified files fail closed.
     *
     * @param password A secret key.
     * @param data The string data to encrypt.
     * @return A versioned container containing the salt, nonce, ciphertext, and authentication tag.
     */
    suspend fun encrypt(password: String, data: String): ByteArray {
        return encrypt(password, data) { passwordBytes, salt, size ->
            HashKeyGenerator.generateHashKey(passwordBytes, salt, size)
        }
    }

    internal suspend fun encrypt(
        password: String,
        data: String,
        deriveKey: CryptoKeyDeriver,
    ): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty." }

        val salt = nextBytes(SALT_SIZE)
        val keys = deriveKey(password.encodeToByteArray(), salt, KEY_SIZE * 2)
        require(keys.size == KEY_SIZE * 2) { "Invalid derived key size." }
        val encryptionKey = keys.copyOfRange(0, KEY_SIZE)
        val authenticationKey = keys.copyOfRange(KEY_SIZE, KEY_SIZE * 2)
        val header = MAGIC + byteArrayOf(VERSION) + salt
        val ciphertext = XChaCha20(encryptionKey).encrypt(data.encodeToByteArray())
        val authenticatedData = header + ciphertext
        val tag = Hmac(Hmac.Type.SHA256, authenticationKey).compute(authenticatedData)

        return authenticatedData + tag
    }

    /**
     * Decrypts an authenticated, versioned encrypted backup.
     *
     * @param password A secret key.
     * @param data The complete encrypted backup container.
     * @return The decrypted string.
     */
    suspend fun decrypt(password: String, data: ByteArray): String {
        return decrypt(password, data) { passwordBytes, salt, size ->
            HashKeyGenerator.generateHashKey(passwordBytes, salt, size)
        }
    }

    internal suspend fun decrypt(
        password: String,
        data: ByteArray,
        deriveKey: CryptoKeyDeriver,
    ): String {
        require(password.isNotEmpty()) { "Password must not be empty." }
        require(data.startsWith(MAGIC)) { "Invalid encrypted backup format." }
        val headerSize = MAGIC.size + 1 + SALT_SIZE
        val minimumSize = headerSize + XChaCha20.NONCE_SIZE + MAC_SIZE
        require(data.size >= minimumSize) { "Invalid encrypted backup." }
        require(data[MAGIC.size] == VERSION) { "Unsupported encrypted backup version." }

        val salt = data.copyOfRange(MAGIC.size + 1, headerSize)
        val keys = deriveKey(password.encodeToByteArray(), salt, KEY_SIZE * 2)
        require(keys.size == KEY_SIZE * 2) { "Invalid derived key size." }
        val encryptionKey = keys.copyOfRange(0, KEY_SIZE)
        val authenticationKey = keys.copyOfRange(KEY_SIZE, KEY_SIZE * 2)
        val authenticatedData = data.copyOfRange(0, data.size - MAC_SIZE)
        val suppliedTag = data.copyOfRange(data.size - MAC_SIZE, data.size)
        val expectedTag = Hmac(Hmac.Type.SHA256, authenticationKey).compute(authenticatedData)
        require(constantTimeEquals(suppliedTag, expectedTag)) { "Invalid password or modified backup." }

        val ciphertext = authenticatedData.copyOfRange(headerSize, authenticatedData.size)
        return XChaCha20(encryptionKey).decrypt(ciphertext).decodeToString(throwOnInvalidSequence = true)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean {
        if (first.size != second.size) return false
        var difference = 0
        for (index in first.indices) {
            difference = difference or (first[index].toInt() xor second[index].toInt())
        }
        return difference == 0
    }
}
