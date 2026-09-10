package com.boxy.authenticator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.cd_delete_label
import boxy_authenticator.composeapp.generated.resources.cd_edit_label
import boxy_authenticator.composeapp.generated.resources.delete_label_dialog_message
import boxy_authenticator.composeapp.generated.resources.delete_label_dialog_title
import boxy_authenticator.composeapp.generated.resources.label_account_count
import boxy_authenticator.composeapp.generated.resources.label_account_count_one
import boxy_authenticator.composeapp.generated.resources.label_name
import boxy_authenticator.composeapp.generated.resources.manage_labels
import boxy_authenticator.composeapp.generated.resources.no_labels
import boxy_authenticator.composeapp.generated.resources.remove
import boxy_authenticator.composeapp.generated.resources.rename
import boxy_authenticator.composeapp.generated.resources.rename_label
import boxy_authenticator.composeapp.generated.resources.retry
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.domain.models.LabelSummary
import com.boxy.authenticator.ui.components.design.BoxyButton
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.components.design.BoxyTextField
import com.boxy.authenticator.ui.components.dialogs.BoxyDialog
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.state.ManageLabelsUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun ManageLabelsScreen(
    uiState: ManageLabelsUiState,
    loadLabels: () -> Unit,
    showRenameDialog: (LabelSummary?) -> Unit,
    updateEditedName: (String) -> Unit,
    renameLabel: () -> Unit,
    showDeleteDialog: (LabelSummary?) -> Unit,
    deleteLabel: () -> Unit,
    clearOperationError: () -> Unit,
    navigateUp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { loadLabels() }
    LaunchedEffect(uiState.operationError) {
        uiState.operationError?.let {
            snackbarHostState.showSnackbar(it)
            clearOperationError()
        }
    }

    BoxyScaffold(
        topBar = {
            Toolbar(
                title = stringResource(Res.string.manage_labels),
                showDefaultNavigationIcon = true,
                onNavigationIconClick = navigateUp,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState.labelsState) {
            DataLoadState.Initial, DataLoadState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DataLoadState.Error -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                BoxyButton(onClick = loadLabels, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(Res.string.retry))
                }
            }

            is DataLoadState.Data -> if (state.value.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(Res.string.no_labels)) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(state.value, key = { it.name.lowercase() }) { label ->
                        ListItem(
                            headlineContent = { Text(label.name) },
                            supportingContent = {
                                Text(
                                    if (label.tokenCount == 1L) {
                                        stringResource(Res.string.label_account_count_one)
                                    } else {
                                        stringResource(
                                            Res.string.label_account_count,
                                            label.tokenCount,
                                        )
                                    }
                                )
                            },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { showRenameDialog(label) }) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = stringResource(
                                                Res.string.cd_edit_label,
                                                label.name,
                                            ),
                                        )
                                    }
                                    IconButton(onClick = { showDeleteDialog(label) }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(
                                                Res.string.cd_delete_label,
                                                label.name,
                                            ),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    uiState.editingLabel?.let {
        BoxyDialog(
            dialogTitle = stringResource(Res.string.rename_label),
            confirmText = stringResource(Res.string.rename),
            confirmEnabled = !uiState.isSaving,
            onDismissRequest = { if (!uiState.isSaving) showRenameDialog(null) },
            onConfirmation = renameLabel,
        ) {
            BoxyTextField(
                value = uiState.editedName,
                onValueChange = updateEditedName,
                label = stringResource(Res.string.label_name),
                errorMessage = uiState.validationError,
                enabled = !uiState.isSaving,
            )
        }
    }

    uiState.deletingLabel?.let { label ->
        BoxyDialog(
            dialogTitle = stringResource(Res.string.delete_label_dialog_title),
            dialogBody = stringResource(
                Res.string.delete_label_dialog_message,
                label.name,
                label.tokenCount,
            ),
            confirmText = stringResource(Res.string.remove),
            confirmEnabled = !uiState.isSaving,
            isDestructive = true,
            onDismissRequest = { if (!uiState.isSaving) showDeleteDialog(null) },
            onConfirmation = deleteLabel,
        )
    }
}
