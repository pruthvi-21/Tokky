package com.boxy.authenticator.domain.models.form

import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.OTPType

sealed class TokenFormEvent {
    data class IssuerChanged(val issuer: String) : TokenFormEvent()
    data class LabelChanged(val label: String) : TokenFormEvent()
    data class NewLabelChanged(val label: String) : TokenFormEvent()
    data object AddLabel : TokenFormEvent()
    data class LabelToggled(val label: String) : TokenFormEvent()
    data class AddLabelDialogVisibilityChanged(val visible: Boolean) : TokenFormEvent()
    data class SecretKeyChanged(val secretKey: String) : TokenFormEvent()
    data class TypeChanged(val type: OTPType) : TokenFormEvent()
    data class ThumbnailChanged(val thumbnail: Thumbnail) : TokenFormEvent()
    data class AlgorithmChanged(val algorithm: String) : TokenFormEvent()
    data class PeriodChanged(val period: String) : TokenFormEvent()
    data class DigitsChanged(val digits: String) : TokenFormEvent()
    data class CounterChanged(val counter: String) : TokenFormEvent()
    data class EnableAdvancedOptionsChanged(val enableAdvancedOptions: Boolean) : TokenFormEvent()
    data class Submit(
        val onComplete: () -> Unit,
        val onDuplicate: (TokenEntry, TokenEntry) -> Unit,
    ) : TokenFormEvent()
}
