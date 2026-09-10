package com.boxy.authenticator.domain.models

import com.boxy.authenticator.domain.models.enums.AccountEntryMethod
import com.boxy.authenticator.domain.models.otp.OtpInfo
import kotlinx.serialization.Serializable

@Serializable
data class ExportableTokenEntry(
    var issuer: String,
    var label: String = "",
    var thumbnail: Thumbnail,
    val otpInfo: OtpInfo,
    val labels: Set<String> = emptySet(),
    val isArchived: Boolean = false,
) {
    fun toTokenEntry(): TokenEntry {
        return TokenEntry.create(
            issuer = issuer,
            label = label,
            thumbnail = thumbnail,
            otpInfo = otpInfo,
            addedFrom = AccountEntryMethod.RESTORED,
            labels = labels,
            isArchived = isArchived,
        )
    }

    companion object {
        fun fromTokenEntry(token: TokenEntry): ExportableTokenEntry {
            return ExportableTokenEntry(
                issuer = token.issuer,
                label = token.label,
                thumbnail = token.thumbnail,
                otpInfo = token.otpInfo,
                labels = token.labels,
                isArchived = token.isArchived,
            )
        }
    }
}
