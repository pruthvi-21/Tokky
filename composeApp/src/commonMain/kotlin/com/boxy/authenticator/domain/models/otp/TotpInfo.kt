package com.boxy.authenticator.domain.models.otp

import com.boxy.authenticator.core.otp.OtpGenerator
import com.boxy.authenticator.core.serialization.ByteArraySerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
@SerialName("totp")
@OptIn(ExperimentalSerializationApi::class)
open class TotpInfo(
    @Serializable(with = ByteArraySerializer::class)
    override val secretKey: ByteArray,
    @EncodeDefault
    override val algorithm: String = DEFAULT_ALGORITHM,
    @EncodeDefault
    override val digits: Int = DEFAULT_DIGITS,
    @EncodeDefault
    val period: Long = DEFAULT_PERIOD,
) : OtpInfo() {

    override fun getOtp(): String {
        val otp = calculateToken()
        return "$otp".takeLast(digits).padStart(digits, '0')
    }

    protected fun calculateToken(): Int {
        val time = Clock.System.now().toEpochMilliseconds()
        val otp = OtpGenerator.generateTotp(secretKey, algorithm, period, time)
        return otp
    }

    fun getMillisTillNextRotation(): Long {
        val p = period * 1000
        return p - (Clock.System.now().toEpochMilliseconds() % p)
    }

    companion object {
        const val DEFAULT_PERIOD = 30L
    }
}
