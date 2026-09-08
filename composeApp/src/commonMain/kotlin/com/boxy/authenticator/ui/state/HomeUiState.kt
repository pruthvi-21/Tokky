package com.boxy.authenticator.ui.state

import com.boxy.authenticator.domain.models.TokenEntry

data class HomeUiState(
    val tokensState: DataLoadState<List<TokenEntry>> = DataLoadState.Initial,
    val isRefreshing: Boolean = false,
    val isFabExpanded: Boolean = false,
    val isLastBackupOutdated: Boolean = false,
    val hasTakenAtleastOneBackup: Boolean = false,
    val isSnackBarVisible: Boolean = true,
)
