package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.ui.state.HomeUiState
import com.boxy.authenticator.ui.state.DataLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val fetchTokensUseCase: FetchTokensUseCase,
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
                withContext(Dispatchers.Default) {
                    Triple(
                        fetchTokensUseCase().getOrThrow(),
                        settingsDataStore.isDisableBackupAlertsEnabled(),
                        settingsDataStore.getLastBackupTimestamp(),
                    )
                }
            }.fold(
                onSuccess = { (tokens, disableBackupAlerts, lastBackupTime) ->

                    _uiState.value = _uiState.value.copy(
                        tokensState = DataLoadState.Data(tokens),
                        isRefreshing = false,
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

    fun dismissSnackbar() {
        _uiState.value = _uiState.value.copy(
            isSnackBarVisible = false,
        )
    }
}
