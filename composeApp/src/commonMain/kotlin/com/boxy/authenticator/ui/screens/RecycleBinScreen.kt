package com.boxy.authenticator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
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
import boxy_authenticator.composeapp.generated.resources.cancel
import boxy_authenticator.composeapp.generated.resources.delete_permanently
import boxy_authenticator.composeapp.generated.resources.permanently_delete_account
import boxy_authenticator.composeapp.generated.resources.permanently_delete_account_message
import boxy_authenticator.composeapp.generated.resources.permanently_delete_account_title
import boxy_authenticator.composeapp.generated.resources.recycle_bin
import boxy_authenticator.composeapp.generated.resources.recycle_bin_empty
import boxy_authenticator.composeapp.generated.resources.restore_recycled_account
import boxy_authenticator.composeapp.generated.resources.retry
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.ui.components.Toolbar
import com.boxy.authenticator.ui.components.design.BoxyButton
import com.boxy.authenticator.ui.components.design.BoxyScaffold
import com.boxy.authenticator.ui.components.dialogs.BoxyDialog
import com.boxy.authenticator.ui.state.DataLoadState
import com.boxy.authenticator.ui.state.RecycleBinUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun RecycleBinScreen(
    uiState: RecycleBinUiState,
    loadTokens: () -> Unit,
    restoreToken: (String) -> Unit,
    showPermanentDeleteDialog: (TokenEntry?) -> Unit,
    permanentlyDeleteToken: () -> Unit,
    clearOperationError: () -> Unit,
    navigateUp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { loadTokens() }
    LaunchedEffect(uiState.operationError) {
        uiState.operationError?.let {
            snackbarHostState.showSnackbar(it)
            clearOperationError()
        }
    }

    BoxyScaffold(
        topBar = {
            Toolbar(
                title = stringResource(Res.string.recycle_bin),
                showDefaultNavigationIcon = true,
                onNavigationIconClick = navigateUp,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState.tokensState) {
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
                BoxyButton(onClick = loadTokens, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(Res.string.retry))
                }
            }

            is DataLoadState.Data -> if (state.value.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(Res.string.recycle_bin_empty)) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(state.value, key = { it.id }) { token ->
                        ListItem(
                            modifier = Modifier.animateItem(),
                            headlineContent = { Text(token.issuer) },
                            supportingContent = { Text(token.label) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { restoreToken(token.id) },
                                        enabled = uiState.processingTokenId == null,
                                    ) {
                                        Icon(
                                            Icons.Outlined.RestoreFromTrash,
                                            contentDescription = stringResource(
                                                Res.string.restore_recycled_account,
                                                token.issuer,
                                            ),
                                        )
                                    }
                                    IconButton(
                                        onClick = { showPermanentDeleteDialog(token) },
                                        enabled = uiState.processingTokenId == null,
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteForever,
                                            contentDescription = stringResource(
                                                Res.string.permanently_delete_account,
                                                token.issuer,
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

    uiState.permanentlyDeletingToken?.let { token ->
        BoxyDialog(
            dialogTitle = stringResource(Res.string.permanently_delete_account_title),
            dialogBody = stringResource(
                Res.string.permanently_delete_account_message,
                token.issuer,
            ),
            dismissText = stringResource(Res.string.cancel),
            confirmText = stringResource(Res.string.delete_permanently),
            confirmEnabled = uiState.processingTokenId == null,
            isDestructive = true,
            onDismissRequest = {
                if (uiState.processingTokenId == null) showPermanentDeleteDialog(null)
            },
            onConfirmation = permanentlyDeleteToken,
        )
    }
}
