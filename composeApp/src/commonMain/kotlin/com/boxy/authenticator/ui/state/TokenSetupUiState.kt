package com.boxy.authenticator.ui.state

import com.boxy.authenticator.domain.models.Thumbnail
import com.boxy.authenticator.domain.models.enums.OTPType
import com.boxy.authenticator.domain.models.enums.TokenSetupMode
import com.boxy.authenticator.domain.models.otp.HotpInfo.Companion.DEFAULT_COUNTER
import com.boxy.authenticator.domain.models.otp.OtpInfo.Companion.DEFAULT_ALGORITHM
import com.boxy.authenticator.domain.models.otp.OtpInfo.Companion.DEFAULT_DIGITS
import com.boxy.authenticator.domain.models.otp.TotpInfo.Companion.DEFAULT_PERIOD
import com.boxy.authenticator.ui.viewmodels.TokenSetupViewModel.DuplicateTokenDialogArgs
import com.boxy.authenticator.utils.Constants.THUMBNAIL_COlORS

data class TokenSetupUiState(
    val editLoadState: DataLoadState<Unit> = DataLoadState.Initial,
    val isSaving: Boolean = false,
    val operationError: String? = null,
    val issuer: String = "",
    val label: String = "",
    val labels: Set<String> = emptySet(),
    val availableLabels: Set<String> = emptySet(),
    val newLabel: String = "",
    val isArchived: Boolean = false,
    val secretKey: String = "",
    val type: OTPType = OTPType.TOTP,
    val thumbnail: Thumbnail = Thumbnail.Color(THUMBNAIL_COlORS.random()),
    val algorithm: String = DEFAULT_ALGORITHM,
    val period: String = "$DEFAULT_PERIOD",
    val digits: String = "$DEFAULT_DIGITS",
    val counter: String = "$DEFAULT_COUNTER",
    val isInEditMode: Boolean = false,
    val enableAdvancedOptions: Boolean = false,
    val isAlgorithmFieldVisible: Boolean = true,
    val isDigitsFieldVisible: Boolean = true,
    val isPeriodFieldVisible: Boolean = true,
    val isCounterFieldVisible: Boolean = false,
    val validationErrors: Map<String, String?> = emptyMap(),

    val tokenSetupMode: TokenSetupMode = TokenSetupMode.NEW,

    val showBackPressDialog: Boolean = false,
    val showDeleteTokenDialog: Boolean = false,
    val showArchiveTokenDialog: Boolean = false,
    val showAddLabelDialog: Boolean = false,
    val showDuplicateTokenDialog: DuplicateTokenDialogArgs = DuplicateTokenDialogArgs(false),
)
