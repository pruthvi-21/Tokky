package com.boxy.authenticator.domain.models.otp

import com.boxy.authenticator.core.serialization.BoxyJson
import com.boxy.authenticator.core.serialization.ByteArraySerializer
import com.boxy.authenticator.utils.OtpInfoException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
sealed class OtpInfo {
    @Serializable(with = ByteArraySerializer::class)
    abstract val secretKey: ByteArray
    abstract val algorithm: String
    abstract val digits: Int

    @Throws(OtpInfoException::class)
    abstract fun getOtp(): String

    open fun serialize(): String {
        return try {
            BoxyJson.encodeToString(this)
        } catch (e: SerializationException) {
            throw SerializationException("Failed to serialize OtpInfo", e)
        }
    }

    companion object {
        const val DEFAULT_DIGITS: Int = 6
        const val DEFAULT_ALGORITHM: String = "SHA1"

        fun deserialize(jsonString: String): OtpInfo {
            return try {
                BoxyJson.decodeFromString(jsonString)
            } catch (e: SerializationException) {
                throw SerializationException("Failed to deserialize OtpInfo", e)
            }
        }
    }
}
