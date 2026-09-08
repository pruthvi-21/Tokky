package com.boxy.authenticator.domain.models.otp

import com.boxy.authenticator.core.otp.OtpGenerator
import com.boxy.authenticator.core.serialization.ByteArraySerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("hotp")
@OptIn(ExperimentalSerializationApi::class)
class HotpInfo(
    @Serializable(with = ByteArraySerializer::class)
    override val secretKey: ByteArray,
    @EncodeDefault
    override val algorithm: String = DEFAULT_ALGORITHM,
    @EncodeDefault
    override val digits: Int = DEFAULT_DIGITS,
    @EncodeDefault
    val counter: Long = DEFAULT_COUNTER,
) : OtpInfo() {

    override fun getOtp(): String {
        val otp = OtpGenerator.generateHotp(secretKey, algorithm, counter)
        return "$otp".takeLast(digits).padStart(digits, '0')
    }

    companion object {
        const val DEFAULT_COUNTER: Long = 0L
        const val COUNTER_MIN_VALUE: Long = 0L
    }
}
