package com.boxy.authenticator.ui.state

import com.boxy.authenticator.domain.models.TokenEntry

data class RecycleBinUiState(
    val tokensState: DataLoadState<List<TokenEntry>> = DataLoadState.Initial,
    val permanentlyDeletingToken: TokenEntry? = null,
    val processingTokenId: String? = null,
    val operationError: String? = null,
)
