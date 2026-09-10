package com.boxy.authenticator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.app_name
import boxy_authenticator.composeapp.generated.resources.archive
import boxy_authenticator.composeapp.generated.resources.close
import boxy_authenticator.composeapp.generated.resources.dismiss
import boxy_authenticator.composeapp.generated.resources.empty_layout_text
import boxy_authenticator.composeapp.generated.resources.expandable_fab_manual_title
import boxy_authenticator.composeapp.generated.resources.expandable_fab_qr_title
import boxy_authenticator.composeapp.generated.resources.no_backup_taken_msg
import boxy_authenticator.composeapp.generated.resources.outdated_backup_msg
import boxy_authenticator.composeapp.generated.resources.retry
import boxy_authenticator.composeapp.generated.resources.title_settings
import boxy_authenticator.composeapp.generated.resources.unable_to_load_accounts
import boxy_authenticator.composeapp.generated.resources.all_labels
import boxy_authenticator.composeapp.generated.resources.apply
import boxy_authenticator.composeapp.generated.resources.apply_labels
import boxy_authenticator.composeapp.generated.resources.apply_labels_message
import boxy_authenticator.composeapp.generated.resources.archive_selected_accounts
import boxy_authenticator.composeapp.generated.resources.archive_selected_accounts_message
import boxy_authenticator.composeapp.generated.resources.delete_selected_accounts
import boxy_authenticator.composeapp.generated.resources.delete_selected_accounts_message
import boxy_authenticator.composeapp.generated.resources.edit_selected_account
import boxy_authenticator.composeapp.generated.resources.no_label_matches
import boxy_authenticator.composeapp.generated.resources.make_default_label
import boxy_authenticator.composeapp.generated.resources.make_default_label_message
import boxy_authenticator.composeapp.generated.resources.manage_labels
import boxy_authenticator.composeapp.generated.resources.more_options
import boxy_authenticator.composeapp.generated.resources.remove_default_label
import boxy_authenticator.composeapp.generated.resources.remove_default_label_message
import boxy_authenticator.composeapp.generated.resources.set_default
import boxy_authenticator.composeapp.generated.resources.remove_default
import boxy_authenticator.composeapp.generated.resources.default_label
import boxy_authenticator.composeapp.generated.resources.archived_accounts
import boxy_authenticator.composeapp.generated.resources.no_active_accounts
import boxy_authenticator.composeapp.generated.resources.no_labels
import boxy_authenticator.composeapp.generated.resources.remove
import boxy_authenticator.composeapp.generated.resources.restore_selected_accounts
import boxy_authenticator.composeapp.generated.resources.restore_selected_accounts_message
import boxy_authenticator.composeapp.generated.resources.restore
import boxy_authenticator.composeapp.generated.resources.selected_accounts
import com.boxy.authenticator.core.Platform
import com.boxy.authenticator.ui.components.ExpandableFab
import com.boxy.authenticator.ui.components.ExpandableFabItem
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.components.design.BoxyButton
import com.boxy.authenticator.ui.components.dialogs.BoxyDialog
import com.boxy.authenticator.ui.screens.home.TokensList
import com.boxy.authenticator.ui.state.HomeUiState
import com.boxy.authenticator.domain.models.enums.LabelVisibility
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.util.SystemBackHandler
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    loadTokens: () -> Unit,
    onFabExpanded: (Boolean) -> Unit,
    onTokenViewed: (String) -> Unit,
    onUpdateHotpCounter: (String, Long, (Boolean) -> Unit) -> Unit,
    onDismissSnackbar: () -> Unit,
    onTokenLongPressed: (String) -> Unit,
    onTokenSelectionToggle: (String) -> Unit,
    onClearSelection: () -> Unit,
    onShowApplyLabelsDialog: (Boolean) -> Unit,
    onToggleLabelToApply: (String) -> Unit,
    onApplyLabelsToSelection: () -> Unit,
    onShowArchiveSelectionDialog: (Boolean) -> Unit,
    onArchiveOrRestoreSelection: () -> Unit,
    onShowDeleteSelectionDialog: (Boolean) -> Unit,
    onDeleteSelection: () -> Unit,
    onClearSelectionError: () -> Unit,
    onLabelFilterToggle: (String?) -> Unit,
    onDefaultLabelRequested: (String) -> Unit,
    onDefaultLabelDismissed: () -> Unit,
    onDefaultLabelConfirmed: () -> Unit,
    onArchivedFilterToggle: () -> Unit,
    onDefaultArchivedRequested: () -> Unit,
    labelVisibility: LabelVisibility,
    showLabelCounts: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToManageLabels: () -> Unit,
    onNavigateToQrScan: () -> Unit,
    onNavigateToNewTokenSetup: () -> Unit,
    onNavigateToEditToken: (String) -> Unit,
) {

    val snackbarHostState = remember { SnackbarHostState() }
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    val labelSnapshot = remember(uiState.tokensState) {
        val availableLabels = uiState.availableLabels
        LabelFilterSnapshot(
            availableLabels = availableLabels,
            labelCounts = availableLabels.associateWith(uiState::labelCount),
            activeTokenCount = uiState.activeTokenCount,
            archivedTokenCount = uiState.archivedTokenCount,
            hasArchivedTokens = uiState.hasArchivedTokens,
            allAvailableLabels = uiState.allAvailableLabels,
        )
    }
    val visibleTokens = remember(
        uiState.tokensState,
        uiState.selectedLabels,
        uiState.isArchivedSelected,
    ) { uiState.visibleTokens }
    LaunchedEffect(Unit) {
        loadTokens()
    }

    LaunchedEffect(
        uiState.tokensState,
        uiState.isLastBackupOutdated,
        uiState.hasTakenAtleastOneBackup,
        uiState.isSnackBarVisible,
    ) {
        if (uiState.tokensState is DataLoadState.Data &&
            (uiState.isLastBackupOutdated || !uiState.hasTakenAtleastOneBackup) &&
            uiState.isSnackBarVisible
        ) {
            val message = when {
                !uiState.hasTakenAtleastOneBackup -> getString(Res.string.no_backup_taken_msg)
                uiState.isLastBackupOutdated -> getString(Res.string.outdated_backup_msg)
                else -> null
            }
            if (message != null) {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = getString(Res.string.dismiss),
                )
                onDismissSnackbar()
            }
        }
    }

    LaunchedEffect(uiState.selectionError) {
        uiState.selectionError?.let {
            snackbarHostState.showSnackbar(it)
            onClearSelectionError()
        }
    }

    SystemBackHandler(enabled = uiState.isSelectionMode) {
        onClearSelection()
    }

    SystemBackHandler(enabled = !uiState.isSelectionMode && uiState.isFabExpanded) {
        onFabExpanded(false)
    }

    BoxyScaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                Toolbar(
                    title = stringResource(
                        Res.string.selected_accounts,
                        uiState.selectedTokenIds.size,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(Res.string.close),
                            )
                        }
                    },
                    actions = {
                        uiState.selectedTokenIds.singleOrNull()?.let { tokenId ->
                            IconButton(
                                onClick = { onNavigateToEditToken(tokenId) },
                                enabled = !uiState.isSelectionOperationRunning,
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = stringResource(Res.string.edit_selected_account),
                                )
                            }
                        }
                        IconButton(
                            onClick = { onShowApplyLabelsDialog(true) },
                            enabled = !uiState.isSelectionOperationRunning,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Label,
                                contentDescription = stringResource(Res.string.apply_labels),
                            )
                        }
                        IconButton(
                            onClick = { onShowArchiveSelectionDialog(true) },
                            enabled = !uiState.isSelectionOperationRunning,
                        ) {
                            Icon(
                                if (uiState.areAllSelectedArchived) Icons.Outlined.Unarchive
                                else Icons.Outlined.Archive,
                                contentDescription = stringResource(
                                    if (uiState.areAllSelectedArchived) {
                                        Res.string.restore_selected_accounts
                                    } else Res.string.archive_selected_accounts
                                ),
                            )
                        }
                        IconButton(
                            onClick = { onShowDeleteSelectionDialog(true) },
                            enabled = !uiState.isSelectionOperationRunning,
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(Res.string.delete_selected_accounts),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            } else {
                Toolbar(
                    title = stringResource(Res.string.app_name),
                    showDefaultNavigationIcon = false,
                    actions = {
                        Box {
                            IconButton(onClick = { isOverflowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(Res.string.more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = isOverflowMenuExpanded,
                                onDismissRequest = { isOverflowMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.manage_labels)) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                                    },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        onNavigateToManageLabels()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.title_settings)) },
                                    leadingIcon = {
                                        Icon(Icons.TwoTone.Settings, contentDescription = null)
                                    },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        onNavigateToSettings()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    ) { safePadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                AnimatedContent(
                    targetState = uiState.tokensState,
                    contentKey = { state ->
                        when (state) {
                            DataLoadState.Initial -> "initial"
                            DataLoadState.Loading -> "loading"
                            is DataLoadState.Error -> "error"
                            is DataLoadState.Data -> if (state.value.isEmpty()) "empty" else "content"
                        }
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "home-data-state",
                ) { state ->
                    when (state) {
                    DataLoadState.Initial, DataLoadState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    is DataLoadState.Data -> if (state.value.isNotEmpty()) {
                        TokensList(
                            tokensList = visibleTokens,
                            viewedTokenIds = uiState.viewedTokenIds,
                            onTokenViewed = onTokenViewed,
                            onUpdateHotpCounter = onUpdateHotpCounter,
                            selectedTokenIds = uiState.selectedTokenIds,
                            onTokenLongPressed = onTokenLongPressed,
                            onTokenSelectionToggle = onTokenSelectionToggle,
                            labelVisibility = labelVisibility,
                            headerContent = {
                                LabelFilters(
                                    uiState = uiState,
                                    snapshot = labelSnapshot,
                                    enabled = !uiState.isSelectionMode,
                                    showLabelCounts = showLabelCounts,
                                    onLabelFilterToggle = onLabelFilterToggle,
                                    onDefaultLabelRequested = onDefaultLabelRequested,
                                    onArchivedFilterToggle = onArchivedFilterToggle,
                                    onDefaultArchivedRequested = onDefaultArchivedRequested,
                                )
                            },
                            emptyMessage = stringResource(
                                if (uiState.selectedLabels.isEmpty() &&
                                    !uiState.isArchivedSelected && labelSnapshot.activeTokenCount == 0
                                ) Res.string.no_active_accounts else Res.string.no_label_matches
                            ),
                        )
                    } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.empty_layout_text),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .padding(horizontal = 36.dp),
                                    textAlign = TextAlign.Center,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f
                                )
                            }
                    }
                    is DataLoadState.Error ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(Res.string.unable_to_load_accounts),
                            color = MaterialTheme.colorScheme.error,
                        )
                        BoxyButton(onClick = loadTokens, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                    }
                }
            }

            if (uiState.isRefreshing && uiState.tokensState is DataLoadState.Data) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    if (Platform.isAndroid) {
        // Not needed in IOS as action sheet is used
        AnimatedVisibility(
            visible = uiState.isFabExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .then(
                        if (!uiState.isFabExpanded) Modifier
                        else Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = uiState.isFabExpanded
                        ) { onFabExpanded(false) }
                    )
            )
        }
    }

    if (!uiState.isSelectionMode) {
        val items = listOf(
            ExpandableFabItem(
                stringResource(Res.string.expandable_fab_qr_title),
                Icons.Outlined.QrCodeScanner
            ),
            ExpandableFabItem(
                stringResource(Res.string.expandable_fab_manual_title),
                Icons.Outlined.Edit
            ),
        )

        ExpandableFab(
            isFabExpanded = uiState.isFabExpanded,
            items = items,
            onItemClick = { index ->
                onFabExpanded(false)
                when (index) {
                    0 -> onNavigateToQrScan()
                    1 -> onNavigateToNewTokenSetup()
                }
            },
            onFabExpandChange = { onFabExpanded(it) },
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(16.dp),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .align(Alignment.BottomCenter),
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                actionColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    if (uiState.showDefaultLabelDialog) {
        val label = if (uiState.isPendingDefaultArchived) {
            stringResource(Res.string.archived_accounts)
        } else uiState.pendingDefaultLabel.orEmpty()
        val isRemoving = if (uiState.isPendingDefaultArchived) {
            uiState.isArchivedDefault
        } else uiState.defaultLabel?.equals(label, ignoreCase = true) == true
        BoxyDialog(
            dialogTitle = stringResource(
                if (isRemoving) Res.string.remove_default_label
                else Res.string.make_default_label
            ),
            dialogBody = stringResource(
                if (isRemoving) Res.string.remove_default_label_message
                else Res.string.make_default_label_message,
                label,
            ),
            confirmText = stringResource(
                if (isRemoving) Res.string.remove_default else Res.string.set_default
            ),
            isDestructive = isRemoving,
            onDismissRequest = onDefaultLabelDismissed,
            onConfirmation = onDefaultLabelConfirmed,
        )
    }

    if (uiState.showApplyLabelsDialog) {
        val selectedLabelKeys = uiState.labelsToApply.mapTo(mutableSetOf()) { it.lowercase() }
        BoxyDialog(
            dialogTitle = stringResource(Res.string.apply_labels),
            dialogBody = stringResource(Res.string.apply_labels_message),
            confirmText = stringResource(Res.string.apply),
            confirmEnabled = uiState.labelsToApply.isNotEmpty() &&
                    !uiState.isSelectionOperationRunning,
            onDismissRequest = { onShowApplyLabelsDialog(false) },
            onConfirmation = onApplyLabelsToSelection,
        ) {
            if (labelSnapshot.allAvailableLabels.isEmpty()) {
                Text(stringResource(Res.string.no_labels))
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    labelSnapshot.allAvailableLabels.forEach { label ->
                        FilterChip(
                            selected = label.lowercase() in selectedLabelKeys,
                            onClick = { onToggleLabelToApply(label) },
                            enabled = !uiState.isSelectionOperationRunning,
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.showArchiveSelectionDialog) {
        val restoring = uiState.areAllSelectedArchived
        BoxyDialog(
            dialogTitle = stringResource(
                if (restoring) Res.string.restore_selected_accounts
                else Res.string.archive_selected_accounts
            ),
            dialogBody = stringResource(
                if (restoring) Res.string.restore_selected_accounts_message
                else Res.string.archive_selected_accounts_message
            ),
            confirmText = stringResource(
                if (restoring) Res.string.restore else Res.string.archive
            ),
            confirmEnabled = !uiState.isSelectionOperationRunning,
            onDismissRequest = { onShowArchiveSelectionDialog(false) },
            onConfirmation = onArchiveOrRestoreSelection,
        )
    }

    if (uiState.showDeleteSelectionDialog) {
        BoxyDialog(
            dialogTitle = stringResource(Res.string.delete_selected_accounts),
            dialogBody = stringResource(
                Res.string.delete_selected_accounts_message,
                uiState.selectedTokenIds.size,
            ),
            confirmText = stringResource(Res.string.remove),
            confirmEnabled = !uiState.isSelectionOperationRunning,
            isDestructive = true,
            onDismissRequest = { onShowDeleteSelectionDialog(false) },
            onConfirmation = onDeleteSelection,
        )
    }
}

@Composable
private fun LabelFilters(
    uiState: HomeUiState,
    snapshot: LabelFilterSnapshot,
    enabled: Boolean,
    showLabelCounts: Boolean,
    onLabelFilterToggle: (String?) -> Unit,
    onDefaultLabelRequested: (String) -> Unit,
    onArchivedFilterToggle: () -> Unit,
    onDefaultArchivedRequested: () -> Unit,
) {
    val selectedLabelKeys = remember(uiState.selectedLabels) {
        uiState.selectedLabels.mapTo(mutableSetOf()) { it.lowercase() }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { Spacer(Modifier.width(10.dp)) }
        item {
            FilterChip(
                selected = uiState.selectedLabels.isEmpty() && !uiState.isArchivedSelected,
                onClick = { onLabelFilterToggle(null) },
                enabled = enabled,
                label = {
                    LabelWithOptionalCount(
                        label = stringResource(Res.string.all_labels),
                        count = snapshot.activeTokenCount.takeIf { showLabelCounts },
                    )
                },
            )
        }
        items(snapshot.availableLabels, key = { it.lowercase() }) { label ->
            Spacer(Modifier.width(8.dp))
            LabelFilterChip(
                label = label,
                count = snapshot.labelCounts[label]?.takeIf { showLabelCounts },
                selected = label.lowercase() in selectedLabelKeys,
                onClick = { onLabelFilterToggle(label) },
                onLongClick = { onDefaultLabelRequested(label) },
                isDefault = uiState.defaultLabel?.equals(label, ignoreCase = true) == true,
                enabled = enabled,
            )
        }
        if (snapshot.hasArchivedTokens) {
            item(key = "archived-filter") {
                Spacer(Modifier.width(8.dp))
                LabelFilterChip(
                    label = stringResource(Res.string.archived_accounts),
                    count = snapshot.archivedTokenCount.takeIf { showLabelCounts },
                    selected = uiState.isArchivedSelected,
                    onClick = onArchivedFilterToggle,
                    onLongClick = onDefaultArchivedRequested,
                    isDefault = uiState.isArchivedDefault,
                    enabled = enabled,
                )
            }
        }
        item { Spacer(Modifier.width(10.dp)) }
    }
}

private data class LabelFilterSnapshot(
    val availableLabels: List<String>,
    val labelCounts: Map<String, Int>,
    val activeTokenCount: Int,
    val archivedTokenCount: Int,
    val hasArchivedTokens: Boolean,
    val allAvailableLabels: List<String>,
)

@Composable
private fun LabelWithOptionalCount(label: String, count: Int?) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (count != null) {
            Text("·", style = MaterialTheme.typography.labelLarge)
            Text(count.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LabelFilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    isDefault: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isDefault) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = stringResource(Res.string.default_label),
                    modifier = Modifier.height(16.dp).width(16.dp),
                )
            }
            LabelWithOptionalCount(label, count)
        }
    }
}
