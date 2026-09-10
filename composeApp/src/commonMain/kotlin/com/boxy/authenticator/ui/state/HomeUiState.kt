package com.boxy.authenticator.ui.state

import com.boxy.authenticator.domain.models.TokenEntry

data class HomeUiState(
    val tokensState: DataLoadState<List<TokenEntry>> = DataLoadState.Initial,
    val isRefreshing: Boolean = false,
    val viewedTokenIds: Set<String> = emptySet(),
    val isFabExpanded: Boolean = false,
    val isLastBackupOutdated: Boolean = false,
    val hasTakenAtleastOneBackup: Boolean = false,
    val isSnackBarVisible: Boolean = true,
    val selectedLabels: Set<String> = emptySet(),
    val isArchivedSelected: Boolean = false,
    val defaultLabel: String? = null,
    val isArchivedDefault: Boolean = false,
    val showDefaultLabelDialog: Boolean = false,
    val pendingDefaultLabel: String? = null,
    val isPendingDefaultArchived: Boolean = false,
    val selectedTokenIds: Set<String> = emptySet(),
    val showApplyLabelsDialog: Boolean = false,
    val labelsToApply: Set<String> = emptySet(),
    val showArchiveSelectionDialog: Boolean = false,
    val showDeleteSelectionDialog: Boolean = false,
    val isSelectionOperationRunning: Boolean = false,
    val selectionError: String? = null,
) {
    val availableLabels: List<String>
        get() = activeTokens.flatMap { it.labels }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    val visibleTokens: List<TokenEntry>
        get() = if (isArchivedSelected) archivedTokens else if (selectedLabels.isEmpty()) {
            activeTokens
        } else {
            activeTokens.filter { token ->
                selectedLabels.all { selected ->
                    token.labels.any { it.equals(selected, ignoreCase = true) }
                }
            }
        }

    val activeTokenCount: Int get() = activeTokens.size
    val archivedTokenCount: Int get() = archivedTokens.size
    val hasArchivedTokens: Boolean get() = archivedTokens.isNotEmpty()
    val isSelectionMode: Boolean get() = selectedTokenIds.isNotEmpty()
    val selectedTokens: List<TokenEntry>
        get() = allTokens.filter { it.id in selectedTokenIds }
    val areAllSelectedArchived: Boolean
        get() = selectedTokens.isNotEmpty() && selectedTokens.all(TokenEntry::isArchived)
    val allAvailableLabels: List<String>
        get() = allTokens.flatMap { it.labels }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun labelCount(label: String): Int = activeTokens.count { token ->
        token.labels.any { it.equals(label, ignoreCase = true) }
    }

    private val allTokens: List<TokenEntry>
        get() = (tokensState as? DataLoadState.Data<List<TokenEntry>>)?.value.orEmpty()

    private val activeTokens: List<TokenEntry> get() = allTokens.filterNot(TokenEntry::isArchived)
    private val archivedTokens: List<TokenEntry> get() = allTokens.filter(TokenEntry::isArchived)
}
