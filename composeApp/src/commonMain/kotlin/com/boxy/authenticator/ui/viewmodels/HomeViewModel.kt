package com.boxy.authenticator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxy.authenticator.core.Logger
import com.boxy.authenticator.core.SettingsDataStore
import com.boxy.authenticator.core.TokenLabels
import com.boxy.authenticator.domain.usecases.DeleteTokensUseCase
import com.boxy.authenticator.domain.usecases.FetchTokensUseCase
import com.boxy.authenticator.domain.usecases.UpdateHotpCounterUseCase
import com.boxy.authenticator.domain.usecases.UpdateTokensUseCase
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.ui.state.HomeUiState
import com.boxy.authenticator.ui.state.DataLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val fetchTokensUseCase: FetchTokensUseCase,
    private val updateHotpCounterUseCase: UpdateHotpCounterUseCase,
    private val updateTokensUseCase: UpdateTokensUseCase,
    private val deleteTokensUseCase: DeleteTokensUseCase,
) : ViewModel() {
    private val logger = Logger("HomeViewModel")

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private var hasAppliedDefaultLabel = false

    fun loadTokens() {
        if (_uiState.value.isRefreshing) return
        val currentState = _uiState.value.tokensState
        _uiState.value = _uiState.value.copy(
            tokensState = if (currentState is DataLoadState.Data) currentState else DataLoadState.Loading,
            isRefreshing = true,
        )
        viewModelScope.launch {
            runCatching {
                HomeLoadResult(
                    tokens = fetchTokensUseCase().getOrThrow(),
                    disableBackupAlerts = settingsDataStore.isDisableBackupAlertsEnabled(),
                    lastBackupTime = settingsDataStore.getLastBackupTimestamp(),
                    defaultLabel = settingsDataStore.getDefaultLabelFilter(),
                    viewedTokenIds = settingsDataStore.getViewedItems().toSet(),
                )
            }.fold(
                onSuccess = { loadResult ->
                    val tokens = loadResult.tokens
                    val persistedDefault = loadResult.defaultLabel
                    val activeTokens = tokens.filterNot { it.isArchived }
                    val archivedTokens = tokens.filter { it.isArchived }
                    val availableLabels = activeTokens.flatMap { it.labels }
                    val isArchivedDefault = persistedDefault == ARCHIVED_FILTER_KEY &&
                            archivedTokens.isNotEmpty()
                    val resolvedDefault = persistedDefault
                        ?.takeUnless { it == ARCHIVED_FILTER_KEY }
                        ?.let { saved ->
                        availableLabels.firstOrNull { it.equals(saved, ignoreCase = true) }
                    }
                    if (persistedDefault != null && resolvedDefault == null && !isArchivedDefault) {
                        runCatching { settingsDataStore.setDefaultLabelFilter(null) }
                            .onFailure { logger.e("Failed to clear a stale default label", it) }
                    }

                    _uiState.value = _uiState.value.copy(
                        tokensState = DataLoadState.Data(tokens),
                        isRefreshing = false,
                        viewedTokenIds = loadResult.viewedTokenIds,
                        hasTakenAtleastOneBackup = loadResult.disableBackupAlerts ||
                                tokens.isEmpty() || loadResult.lastBackupTime != -1L,
                        isLastBackupOutdated = !loadResult.disableBackupAlerts &&
                                tokens.isNotEmpty() &&
                                loadResult.lastBackupTime != -1L &&
                                tokens.any { it.updatedOn > loadResult.lastBackupTime },
                        selectedLabels = if (!hasAppliedDefaultLabel) {
                            resolvedDefault?.let(::setOf).orEmpty()
                        } else {
                            _uiState.value.selectedLabels.filter { selected ->
                                activeTokens.any { token ->
                                    token.labels.any { it.equals(selected, ignoreCase = true) }
                                }
                            }.toSet()
                        },
                        isArchivedSelected = if (!hasAppliedDefaultLabel) {
                            isArchivedDefault
                        } else {
                            _uiState.value.isArchivedSelected && archivedTokens.isNotEmpty()
                        },
                        defaultLabel = resolvedDefault,
                        isArchivedDefault = isArchivedDefault,
                        selectedTokenIds = _uiState.value.selectedTokenIds
                            .intersect(tokens.mapTo(mutableSetOf()) { it.id }),
                    )
                    hasAppliedDefaultLabel = true
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

    fun toggleLabelFilter(label: String?) {
        _uiState.value = _uiState.value.copy(
            selectedLabels = if (label == null) {
                emptySet()
            } else if (_uiState.value.selectedLabels.any { it.equals(label, ignoreCase = true) }) {
                _uiState.value.selectedLabels.filterNot { it.equals(label, ignoreCase = true) }.toSet()
            } else {
                _uiState.value.selectedLabels + label
            },
            isArchivedSelected = false,
            selectedTokenIds = emptySet(),
        )
    }

    fun toggleArchivedFilter() {
        _uiState.value = _uiState.value.copy(
            isArchivedSelected = !_uiState.value.isArchivedSelected,
            selectedLabels = emptySet(),
            selectedTokenIds = emptySet(),
        )
    }

    fun selectToken(tokenId: String) {
        if ((_uiState.value.tokensState as? DataLoadState.Data)?.value?.none { it.id == tokenId } != false) {
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedTokenIds = _uiState.value.selectedTokenIds + tokenId,
            isFabExpanded = false,
        )
    }

    fun toggleTokenSelection(tokenId: String) {
        val selectedIds = _uiState.value.selectedTokenIds
        _uiState.value = _uiState.value.copy(
            selectedTokenIds = if (tokenId in selectedIds) selectedIds - tokenId
            else selectedIds + tokenId,
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTokenIds = emptySet(),
            showApplyLabelsDialog = false,
            labelsToApply = emptySet(),
            showArchiveSelectionDialog = false,
            showDeleteSelectionDialog = false,
        )
    }

    fun showApplyLabelsDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showApplyLabelsDialog = show,
            labelsToApply = emptySet(),
        )
    }

    fun toggleLabelToApply(label: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            labelsToApply = if (state.labelsToApply.any { it.equals(label, ignoreCase = true) }) {
                state.labelsToApply.filterNot { it.equals(label, ignoreCase = true) }.toSet()
            } else {
                TokenLabels.normalize(state.labelsToApply + label)
            },
        )
    }

    fun applyLabelsToSelection() {
        val state = _uiState.value
        if (state.selectedTokens.isEmpty() || state.labelsToApply.isEmpty() ||
            state.isSelectionOperationRunning
        ) return
        val updates = state.selectedTokens.map { token ->
            token.copy(labels = TokenLabels.normalize(token.labels + state.labelsToApply))
        }
        updateSelectedTokens(updates)
    }

    fun showArchiveSelectionDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showArchiveSelectionDialog = show)
    }

    fun archiveOrRestoreSelection() {
        val state = _uiState.value
        if (state.selectedTokens.isEmpty() || state.isSelectionOperationRunning) return
        val archive = !state.areAllSelectedArchived
        updateSelectedTokens(state.selectedTokens.map { it.copy(isArchived = archive) })
    }

    fun showDeleteSelectionDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteSelectionDialog = show)
    }

    fun deleteSelection() {
        val state = _uiState.value
        if (state.selectedTokenIds.isEmpty() || state.isSelectionOperationRunning) return
        val selectedIds = state.selectedTokenIds
        _uiState.value = state.copy(isSelectionOperationRunning = true)
        viewModelScope.launch {
            deleteTokensUseCase(selectedIds).fold(
                onSuccess = {
                    val tokens = currentTokens().filterNot { it.id in selectedIds }
                    finishSelectionOperation(tokens)
                },
                onFailure = {
                    logger.e("Failed to delete selected accounts", it)
                    failSelectionOperation("Unable to delete selected accounts.")
                },
            )
        }
    }

    fun clearSelectionError() {
        _uiState.value = _uiState.value.copy(selectionError = null)
    }

    private fun updateSelectedTokens(updates: List<TokenEntry>) {
        _uiState.value = _uiState.value.copy(isSelectionOperationRunning = true)
        viewModelScope.launch {
            updateTokensUseCase(updates).fold(
                onSuccess = {
                    val updatesById = updates.associateBy(TokenEntry::id)
                    val tokens = currentTokens().map { updatesById[it.id] ?: it }
                    finishSelectionOperation(tokens)
                },
                onFailure = {
                    logger.e("Failed to update selected accounts", it)
                    failSelectionOperation("Unable to update selected accounts.")
                },
            )
        }
    }

    private fun currentTokens(): List<TokenEntry> =
        (_uiState.value.tokensState as? DataLoadState.Data)?.value.orEmpty()

    private fun finishSelectionOperation(tokens: List<TokenEntry>) {
        _uiState.value = _uiState.value.copy(
            tokensState = DataLoadState.Data(tokens),
            selectedTokenIds = emptySet(),
            showApplyLabelsDialog = false,
            labelsToApply = emptySet(),
            showArchiveSelectionDialog = false,
            showDeleteSelectionDialog = false,
            isSelectionOperationRunning = false,
            isArchivedSelected = _uiState.value.isArchivedSelected && tokens.any(TokenEntry::isArchived),
        )
    }

    private fun failSelectionOperation(message: String) {
        _uiState.value = _uiState.value.copy(
            isSelectionOperationRunning = false,
            selectionError = message,
        )
    }

    fun requestDefaultLabel(label: String) {
        _uiState.value = _uiState.value.copy(
            showDefaultLabelDialog = true,
            pendingDefaultLabel = label,
            isPendingDefaultArchived = false,
        )
    }

    fun requestDefaultArchived() {
        _uiState.value = _uiState.value.copy(
            showDefaultLabelDialog = true,
            pendingDefaultLabel = null,
            isPendingDefaultArchived = true,
        )
    }

    fun dismissDefaultLabelDialog() {
        _uiState.value = _uiState.value.copy(
            showDefaultLabelDialog = false,
            pendingDefaultLabel = null,
            isPendingDefaultArchived = false,
        )
    }

    fun confirmDefaultLabel() {
        val state = _uiState.value
        val requested = state.pendingDefaultLabel
        if (requested == null && !state.isPendingDefaultArchived) return
        val isCurrentDefault = if (state.isPendingDefaultArchived) {
            state.isArchivedDefault
        } else {
            state.defaultLabel?.equals(requested, ignoreCase = true) == true
        }
        val newDefault = when {
            isCurrentDefault -> null
            state.isPendingDefaultArchived -> ARCHIVED_FILTER_KEY
            else -> requested
        }
        val saved = runCatching { settingsDataStore.setDefaultLabelFilter(newDefault) }
            .onFailure { logger.e("Failed to save the default label", it) }
            .isSuccess
        if (!saved) {
            dismissDefaultLabelDialog()
            return
        }
        _uiState.value = _uiState.value.copy(
            defaultLabel = newDefault?.takeUnless { it == ARCHIVED_FILTER_KEY },
            isArchivedDefault = newDefault == ARCHIVED_FILTER_KEY,
            selectedLabels = newDefault?.takeUnless { it == ARCHIVED_FILTER_KEY }?.let(::setOf).orEmpty(),
            isArchivedSelected = newDefault == ARCHIVED_FILTER_KEY,
            showDefaultLabelDialog = false,
            pendingDefaultLabel = null,
            isPendingDefaultArchived = false,
        )
    }

    private companion object {
        const val ARCHIVED_FILTER_KEY = "boxy:filter:archived"
    }
}

private data class HomeLoadResult(
    val tokens: List<TokenEntry>,
    val disableBackupAlerts: Boolean,
    val lastBackupTime: Long,
    val defaultLabel: String?,
    val viewedTokenIds: Set<String>,
)
