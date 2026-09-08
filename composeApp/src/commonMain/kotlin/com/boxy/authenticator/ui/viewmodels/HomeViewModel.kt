package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.UpdateHotpCounterUseCase
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.ui.state.HomeUiState
import com.boxy.authenticator.ui.state.DataLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val fetchTokensUseCase: FetchTokensUseCase,
    private val updateHotpCounterUseCase: UpdateHotpCounterUseCase,
) : ViewModel() {
    private val logger = Logger("HomeViewModel")

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    fun loadTokens() {
        if (_uiState.value.isRefreshing) return
        val currentState = _uiState.value.tokensState
        _uiState.value = _uiState.value.copy(
            tokensState = if (currentState is DataLoadState.Data) currentState else DataLoadState.Loading,
            isRefreshing = true,
        )
        viewModelScope.launch {
            runCatching {
                Triple(
                    fetchTokensUseCase().getOrThrow(),
                    settingsDataStore.isDisableBackupAlertsEnabled(),
                    settingsDataStore.getLastBackupTimestamp(),
                )
            }.fold(
                onSuccess = { (tokens, disableBackupAlerts, lastBackupTime) ->

                    _uiState.value = _uiState.value.copy(
                        tokensState = DataLoadState.Data(tokens),
                        isRefreshing = false,
                        viewedTokenIds = settingsDataStore.getViewedItems().toSet(),
                        hasTakenAtleastOneBackup = disableBackupAlerts || tokens.isEmpty() || lastBackupTime != -1L,
                        isLastBackupOutdated = !disableBackupAlerts &&
                                tokens.isNotEmpty() &&
                                lastBackupTime != -1L &&
                                tokens.any { it.updatedOn > lastBackupTime },
                    )
                },
                onFailure = { exception ->
                    logger.e(exception.message, exception)
                    _uiState.value = _uiState.value.copy(
                        tokensState = DataLoadState.Error(exception.message ?: "Unknown error"),
                        isRefreshing = false,
                    )
                }
            )
        }
    }

    fun setIsFabExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(
            isFabExpanded = expanded,
        )
    }

    fun markTokenViewed(tokenId: String) {
        if (tokenId in _uiState.value.viewedTokenIds) return
        runCatching { settingsDataStore.markItemAsViewed(tokenId) }
            .onFailure { logger.e("Failed to mark token as viewed", it) }
        _uiState.value = _uiState.value.copy(
            viewedTokenIds = _uiState.value.viewedTokenIds + tokenId,
        )
    }

    fun updateHotpCounter(tokenId: String, counter: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = updateHotpCounterUseCase(tokenId, counter)
                .onFailure { logger.e("Failed to update HOTP counter", it) }
            if (result.isSuccess) {
                val current = _uiState.value.tokensState as? DataLoadState.Data
                if (current != null) {
                    val updatedTokens = current.value.map { token ->
                        val info = token.otpInfo
                        if (token.id == tokenId && info is HotpInfo) {
                            token.copy(
                                otpInfo = HotpInfo(
                                    secretKey = info.secretKey,
                                    algorithm = info.algorithm,
                                    digits = info.digits,
                                    counter = counter,
                                )
                            )
                        } else token
                    }
                    _uiState.value = _uiState.value.copy(
                        tokensState = DataLoadState.Data(updatedTokens),
                    )
                }
            }
            onComplete(result.isSuccess)
        }
    }

    fun dismissSnackbar() {
        _uiState.value = _uiState.value.copy(
            isSnackBarVisible = false,
        )
    }
}
