package com.boxy.authenticator.core.crypto

import diglol.crypto.Argon2

object HashKeyGenerator {
    suspend fun generateHashKey(
        password: ByteArray,
        salt: ByteArray = ByteArray(32),
        hashSize: Int = 32,
    ): ByteArray {
        val kdf = Argon2(
            version = Argon2.Version.V13,
            type = Argon2.Type.ID,
            iterations = 3,
            memory = 65536,
            parallelism = 2,
            hashSize = hashSize,
        )
        return kdf.deriveKey(password, salt)
    }

    suspend fun generateHashKey(password: String): String {
        return generateHashKey(password.encodeToByteArray()).decodeToString()
    }
}
