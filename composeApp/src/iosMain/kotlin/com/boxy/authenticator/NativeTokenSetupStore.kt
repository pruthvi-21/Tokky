package com.boxy.authenticator

import com.boxy.authenticator.core.TokenEntryParser
import com.boxy.authenticator.core.TokenFormValidator
import com.boxy.authenticator.core.TokenLabels
import com.boxy.authenticator.core.encoding.Base32
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.enums.OTPType
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.OtpInfo
import com.boxy.authenticator.domain.models.otp.SteamInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.domain.usecases.InsertTokenUseCase
import com.boxy.authenticator.utils.TokenNameExistsException
import com.boxy.authenticator.utils.cleanSecretKey
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NativeTokenSetupStore : KoinComponent {
    private val insertTokenUseCase: InsertTokenUseCase by inject()
    private val validator: TokenFormValidator by inject()
    private val scope = MainScope()

    init {
        NativeAppBootstrap.start()
    }

    fun addManual(
        issuer: String,
        label: String,
        secretKey: String,
        typeName: String,
        period: String,
        counter: String,
        labelsText: String,
        onComplete: (NativeSaveResult) -> Unit,
    ) {
        scope.launch {
            val type = runCatching { OTPType.valueOf(typeName) }.getOrDefault(OTPType.TOTP)
            val normalizedSecret = secretKey.cleanSecretKey()
            val validationError = firstValidationError(
                issuer = issuer,
                secretKey = normalizedSecret,
                type = type,
                period = period,
                counter = counter,
            )
            if (validationError != null) {
                onComplete(NativeSaveResult(false, validationError))
                return@launch
            }

            val otpInfo = runCatching {
                buildOtpInfo(type, normalizedSecret, period, counter)
            }.getOrElse {
                onComplete(NativeSaveResult(false, "Unable to create this account."))
                return@launch
            }

            val labels = runCatching {
                TokenLabels.normalize(
                    labelsText.split(",")
                        .map(String::trim)
                        .filter(String::isNotBlank)
                )
            }.getOrElse {
                onComplete(NativeSaveResult(false, "Labels must be 1-${TokenLabels.MAX_LENGTH} characters."))
                return@launch
            }

            val token = TokenEntry.create(
                issuer = issuer.trim(),
                label = label.trim(),
                otpInfo = otpInfo,
                addedFrom = AccountEntryMethod.FORM,
                labels = labels,
            )

            insertTokenUseCase(token).fold(
                onSuccess = { onComplete(NativeSaveResult(true, null)) },
                onFailure = { onComplete(NativeSaveResult(false, it.toNativeMessage())) },
            )
        }
    }

    fun addFromUrl(url: String, onComplete: (NativeSaveResult) -> Unit) {
        scope.launch {
            val token = runCatching { TokenEntryParser.buildFromUrl(url) }.getOrElse {
                onComplete(NativeSaveResult(false, "This QR code is not a valid authenticator account."))
                return@launch
            }

            insertTokenUseCase(token).fold(
                onSuccess = { onComplete(NativeSaveResult(true, null)) },
                onFailure = { onComplete(NativeSaveResult(false, it.toNativeMessage())) },
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun firstValidationError(
        issuer: String,
        secretKey: String,
        type: OTPType,
        period: String,
        counter: String,
    ): String? {
        if (validator.validateIssuer(issuer) is TokenFormValidator.Result.Failure) {
            return "Issuer is required."
        }
        if (validator.validateSecretKey(secretKey) is TokenFormValidator.Result.Failure) {
            return "Secret key is invalid."
        }
        if (type == OTPType.TOTP && validator.validatePeriod(period) is TokenFormValidator.Result.Failure) {
            return "Period must be a valid number of seconds."
        }
        if (type == OTPType.HOTP && validator.validateCounter(counter) is TokenFormValidator.Result.Failure) {
            return "Counter must be a valid number."
        }
        return null
    }

    private fun buildOtpInfo(
        type: OTPType,
        secretKey: String,
        period: String,
        counter: String,
    ): OtpInfo {
        val decoded = Base32.decode(secretKey)
        return when (type) {
            OTPType.TOTP -> TotpInfo(decoded, period = period.toLong())
            OTPType.HOTP -> HotpInfo(decoded, counter = counter.toLong())
            OTPType.STEAM -> SteamInfo(decoded)
        }
    }

    private fun Throwable.toNativeMessage(): String {
        return when (this) {
            is TokenNameExistsException -> message ?: "An account with this issuer and label already exists."
            else -> message ?: "Unable to save this account."
        }
    }
}

data class NativeSaveResult(
    val success: Boolean,
    val message: String?,
)
