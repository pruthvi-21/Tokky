package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.ui.state.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val fetchTokensUseCase: FetchTokensUseCase,
) : ViewModel() {
    private val logger = Logger("HomeViewModel")

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    fun loadTokens() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            fetchTokensUseCase().fold(
                onSuccess = { tokens ->
                    val disableBackupAlerts = settingsDataStore.isDisableBackupAlertsEnabled()
                    val lastBackupTime = settingsDataStore.getLastBackupTimestamp()

                    if (disableBackupAlerts || tokens.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            hasTakenAtleastOneBackup = true,
                            isLastBackupOutdated = false,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            hasTakenAtleastOneBackup = lastBackupTime != -1L,
                            isLastBackupOutdated = tokens.any { it.updatedOn > lastBackupTime },
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        tokens = tokens,
                        isInitialLoadComplete = true,
                        isLoading = false,
                        error = null,
                    )
                },
                onFailure = { exception ->
                    logger.e(exception.message, exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Unknown error",
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
