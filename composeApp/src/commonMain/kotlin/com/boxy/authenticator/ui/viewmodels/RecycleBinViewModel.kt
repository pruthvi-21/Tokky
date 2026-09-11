package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.usecases.FetchRecycledTokensUseCase
import com.boxy.authenticator.domain.usecases.PermanentlyDeleteTokensUseCase
import com.boxy.authenticator.domain.usecases.RestoreTokensUseCase
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.state.RecycleBinUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecycleBinViewModel(
    private val fetchRecycledTokensUseCase: FetchRecycledTokensUseCase,
    private val restoreTokensUseCase: RestoreTokensUseCase,
    private val permanentlyDeleteTokensUseCase: PermanentlyDeleteTokensUseCase,
) : ViewModel() {
    private val logger = Logger("RecycleBinViewModel")
    private val _uiState = MutableStateFlow(RecycleBinUiState())
    val uiState = _uiState.asStateFlow()

    fun loadTokens() {
        if (_uiState.value.tokensState == DataLoadState.Loading) return
        _uiState.value = _uiState.value.copy(tokensState = DataLoadState.Loading)
        viewModelScope.launch {
            fetchRecycledTokensUseCase().fold(
                onSuccess = { tokens ->
                    _uiState.value = _uiState.value.copy(
                        tokensState = DataLoadState.Data(tokens),
                    )
                },
                onFailure = {
                    logger.e("Failed to load recycled accounts", it)
                    _uiState.value = _uiState.value.copy(
                        tokensState = DataLoadState.Error("Unable to load Recycle Bin."),
                    )
                },
            )
        }
    }

    fun restoreToken(tokenId: String) {
        if (_uiState.value.processingTokenId != null) return
        if (currentTokens().none { it.id == tokenId }) return
        _uiState.value = _uiState.value.copy(processingTokenId = tokenId)
        viewModelScope.launch {
            restoreTokensUseCase(setOf(tokenId)).fold(
                onSuccess = { finishTokenOperation(tokenId) },
                onFailure = {
                    logger.e("Failed to restore account", it)
                    failTokenOperation("Unable to restore the account.")
                },
            )
        }
    }

    fun showPermanentDeleteDialog(token: TokenEntry?) {
        if (_uiState.value.processingTokenId != null) return
        _uiState.value = _uiState.value.copy(permanentlyDeletingToken = token)
    }

    fun permanentlyDeleteToken() {
        if (_uiState.value.processingTokenId != null) return
        val token = _uiState.value.permanentlyDeletingToken ?: return
        _uiState.value = _uiState.value.copy(processingTokenId = token.id)
        viewModelScope.launch {
            permanentlyDeleteTokensUseCase(setOf(token.id)).fold(
                onSuccess = { finishTokenOperation(token.id) },
                onFailure = {
                    logger.e("Failed to permanently delete account", it)
                    failTokenOperation("Unable to permanently delete the account.")
                },
            )
        }
    }

    fun clearOperationError() {
        _uiState.value = _uiState.value.copy(operationError = null)
    }

    private fun currentTokens(): List<TokenEntry> =
        (_uiState.value.tokensState as? DataLoadState.Data)?.value.orEmpty()

    private fun finishTokenOperation(tokenId: String) {
        _uiState.value = _uiState.value.copy(
            tokensState = DataLoadState.Data(currentTokens().filterNot { it.id == tokenId }),
            permanentlyDeletingToken = null,
            processingTokenId = null,
        )
    }

    private fun failTokenOperation(message: String) {
        _uiState.value = _uiState.value.copy(
            processingTokenId = null,
            operationError = message,
        )
    }
}
