package com.boxy.authenticator.core.encoding

actual object Base32 {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    actual fun decode(s: String): ByteArray {
        return decodeBase32(s)
    }

    actual fun encode(data: ByteArray): String {
        return encodeBase32(data)
    }

    private fun decodeBase32(input: String): ByteArray {
        val sanitizedInput = input
            .filterNot { it.isWhitespace() || it == '=' }
            .uppercase()
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (char in sanitizedInput) {
            val index = BASE32_ALPHABET.indexOf(char)
            if (index == -1) throw IllegalArgumentException("Invalid Base32 character: $char")

            buffer = (buffer shl 5) or index
            bitsLeft += 5

            if (bitsLeft >= 8) {
                output.add((buffer shr (bitsLeft - 8) and 0xFF).toByte())
                bitsLeft -= 8
                buffer = buffer and ((1 shl bitsLeft) - 1)
            }
        }

        return output.toByteArray()
    }

    private fun encodeBase32(data: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8

            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                output.append(BASE32_ALPHABET[index])
                bitsLeft -= 5
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            output.append(BASE32_ALPHABET[index])
        }

        return output.toString()
    }
}
