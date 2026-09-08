package com.boxy.authenticator.ui.state

import com.boxy.authenticator.domain.models.TokenEntry

data class ExportUiState(
    val showPlainTextWarningDialog: Boolean = false,
    val showSetPasswordDialog: Boolean = false,
    val tokensState: DataLoadState<List<TokenEntry>> = DataLoadState.Initial,
    val isExporting: Boolean = false,
)
