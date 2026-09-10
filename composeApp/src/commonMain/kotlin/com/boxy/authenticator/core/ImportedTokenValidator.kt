package com.boxy.authenticator.core

import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo

/** Applies trust-boundary checks to token data before it reaches the database or OTP engine. */
object ImportedTokenValidator {
    private const val MAX_ISSUER_LENGTH = 256
    private const val MAX_LABEL_LENGTH = 512
    private const val MAX_SECRET_BYTES = 1_024
    private const val MAX_PERIOD_SECONDS = 86_400L
    private val SUPPORTED_ALGORITHMS = setOf("SHA1", "SHA256", "SHA512")
    private val VALID_COLOR = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun validate(token: TokenEntry): TokenEntry {
        require(token.issuer.isNotBlank() && token.issuer.length <= MAX_ISSUER_LENGTH) {
            "Invalid issuer."
        }
        require(token.label.length <= MAX_LABEL_LENGTH) { "Invalid label." }
        require(token.otpInfo.secretKey.isNotEmpty() && token.otpInfo.secretKey.size <= MAX_SECRET_BYTES) {
            "Invalid secret key."
        }
        require(token.otpInfo.algorithm in SUPPORTED_ALGORITHMS) { "Unsupported OTP algorithm." }
        require(token.otpInfo.digits in 4..10) { "Invalid OTP digit count." }

        when (val otpInfo = token.otpInfo) {
            is SteamInfo -> Unit
            is TotpInfo -> require(otpInfo.period in 1..MAX_PERIOD_SECONDS) { "Invalid TOTP period." }
            is HotpInfo -> require(otpInfo.counter >= 0) { "Invalid HOTP counter." }
        }

        if (token.thumbnail is Thumbnail.Color) {
            require(VALID_COLOR.matches(token.thumbnail.color)) { "Invalid thumbnail color." }
        }
        val normalizedLabels = TokenLabels.normalize(token.labels)
        return if (normalizedLabels == token.labels) token else token.copy(labels = normalizedLabels)
    }
}
