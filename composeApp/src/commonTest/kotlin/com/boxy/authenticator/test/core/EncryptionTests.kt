package com.boxy.authenticator.test.core

import com.boxy.authenticator.core.crypto.Crypto
import com.boxy.authenticator.core.crypto.CryptoKeyDeriver
import kotlinx.coroutines.test.runTest
import kotlin.experimental.xor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EncryptionTests {

    private val password = "a_secure_password"

    private val testKeyDeriver: CryptoKeyDeriver = { passwordBytes, salt, size ->
        ByteArray(size) { index ->
            (passwordBytes[index % passwordBytes.size].toInt() xor
                    salt[index % salt.size].toInt() xor index).toByte()
        }
    }

    private suspend fun encrypt(password: String, data: String): ByteArray =
        Crypto.encrypt(password, data, testKeyDeriver)

    private suspend fun decrypt(password: String, data: ByteArray): String =
        Crypto.decrypt(password, data, testKeyDeriver)

    @Test
    fun testEncryptionNotEmpty() = runTest {
        val data = "Some data to encrypt"

        val encryptedData = encrypt(password, data)
        assertTrue(encryptedData.isNotEmpty(), "Encrypted data should not be empty.")
    }

    @Test
    fun testEncryptionAndDecryption() = runTest {
        val originalData = "Some data to encrypt"

        val encryptedData = encrypt(password, originalData)
        val decryptedData = decrypt(password, encryptedData)

        assertEquals(originalData, decryptedData, "Decrypted data should match the original.")
    }

    @Test
    fun testEmptyEncryptionAndDecryption() = runTest {
        val inputData = ""

        val encryptedData = encrypt(password, inputData)
        val decryptedData = decrypt(password, encryptedData)

        assertEquals(
            inputData,
            decryptedData,
            "Decryption of empty string should return empty string."
        )
    }

    @Test
    fun testEncryptionOfEmptyInput() = runTest {
        val data = ""

        val encryptedData = encrypt(password, data)
        assertTrue(
            encryptedData.isNotEmpty(),
            "Encrypted data for an empty string should not be empty."
        )
    }

    @Test
    fun testEncryptionShouldProducesDifferentOutputsForSameInput() = runTest {
        val data = "Some sensitive information"

        val encryptedData1 = encrypt(password, data)
        val encryptedData2 = encrypt(password, data)

        assertNotEquals(
            encryptedData1,
            encryptedData2,
            "Each encryption should produce a different output due to different nonce."
        )
    }

    @Test
    fun testDecryptionWithWrongKeyFails() = runTest {
        val data = "This should not decrypt correctly"

        val encryptedData = encrypt(password, data)
        assertFailsWith<IllegalArgumentException> {
            decrypt("a_wrong_password", encryptedData)
        }
    }

    @Test
    fun testDecryptionOfTamperedCiphertextFails() = runTest {
        val data = "Tamper test"

        val encryptedData = encrypt(password, data)
        // Flip a byte
        val tamperedData = encryptedData.copyOf().apply { this[25] = (this[25] xor 0xFF.toByte()) }
        assertFailsWith<IllegalArgumentException> {
            decrypt(password, tamperedData)
        }
    }

    @Test
    fun testEncryptionOfLargeData() = runTest {
        val largeData = "A".repeat(1_000_000)

        val encryptedData = encrypt(password, largeData)
        val decryptedData = decrypt(password, encryptedData)

        assertEquals(largeData, decryptedData, "Decryption of large data should be accurate")
    }

    @Test
    fun testInvalidCiphertextThrowsError() = runTest {
        val invalidData = ByteArray(10) { it.toByte() }

        assertFailsWith<Exception> {
            decrypt(password, invalidData)
        }
    }

    @Test
    fun testUnversionedCiphertextIsRejected() = runTest {
        val oldOrUnknownData = ByteArray(128) { it.toByte() }

        assertFailsWith<IllegalArgumentException> {
            decrypt(password, oldOrUnknownData)
        }
    }

    @Test
    fun testNoncePrependingWorks() = runTest {
        val data = "Nonce test"

        val encryptedData = encrypt(password, data)

        assertTrue(encryptedData.size > 24, "Encrypted data should contain a header, nonce, and MAC")
    }
}
